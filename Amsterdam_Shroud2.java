import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.measure.ResultsTable;
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author cradeloso
 */
public class Amsterdam_Shroud2 implements PlugInFilter{
        protected ImagePlus imp; //mi serie de imagenes XVi
	protected ImageStack stack; //stack asociado a las imagenes XVi
        protected ImageProcessor ip; 
        
        protected ImagePlus imp2; //el Amsterdam_Shroud
	protected ImageStack stack2; //el stack del Amsterdam_Shroud (solo tiene una slice)
	protected ImageProcessor ip2;
        int APini, APend, PAini, PAend;
        int iSmooth,iInhales, iThreshold;
        double dUmbral;
        String sTitle;
        String sHISdate;
                
        public int setup(String argv, ImagePlus imp){
		this.imp = imp;
		try{
			stack = imp.getStack();
		}
		catch(Exception e){
			IJ.showMessage("A stack must be open.");
			return DONE;
		}
		return DOES_16+STACK_REQUIRED+NO_CHANGES;
	}
        public void run(ImageProcessor ip){
	    APini = 40;
            APend = 140;
            PAini = 220;
            PAend = 320;
            iThreshold = 20000;
            iSmooth = 5;
            dUmbral = 0.999;
            iInhales = 10;
            sTitle = stack.getSliceLabel(1);
            sHISdate = getHISdate(sTitle);
            
            GenericDialog d1 = new GenericDialog("Amsterdam Shroud Analisis");
            d1.addNumericField("Threshold", iThreshold, 0);
            d1.addNumericField("indice smooth", iSmooth, 0);
            d1.addNumericField("indice umbral", dUmbral, 4);
            d1.addNumericField("indice inhales", iInhales, 0);
            d1.showDialog();
            if(d1.wasCanceled()) return;
            iThreshold = (int)d1.getNextNumber();
            iSmooth = (int)d1.getNextNumber();
            dUmbral = (double)d1.getNextNumber();
            iInhales = (int)d1.getNextNumber();
            
            imp2 = getAmsShroud(imp);
            imp2.show();
            
            float X[] = getRespPatternX(imp2);
            float Y[] = smoothPattern(getRespPatternY(imp2),iSmooth);//hago un suavizado de la curva Y de grado 5

            PlotWindow.noGridLines = false;
            Plot plot = new Plot("Respiratoy Pattern","Gantry Angle","Amplitud",X,Y);
            plot.setLimits(0, 360, 0, imp2.getHeight());
            plot.setLineWidth(1);
            plot.show();
            
            GenericDialog d = new GenericDialog("Amsterdam Shroud Analisis");
            d.addNumericField("Gantry AP_ini", APini, 0);
            d.addNumericField("Gantry AP_end", APend, 0);
            d.addNumericField("Gantry PA_ini", PAini, 0);
            d.addNumericField("Gantry PA_end", PAend, 0);
            
            d.showDialog();
            if(d.wasCanceled()) return;
            APini = (int)d.getNextNumber();
            APend = (int)d.getNextNumber();
            PAini = (int)d.getNextNumber();
            PAend = (int)d.getNextNumber();
            
            float InhalesY[]=getInhalesY(X,Y,iInhales);
            float InhalesX[]=getInhalesX(X,Y,InhalesY);
            float ExhalesY[]=getExhalesY(X,Y,iInhales);
            float ExhalesX[]=getExhalesX(X,Y,ExhalesY);
            
            plot.setColor(Color.red);
            plot.addPoints(InhalesX, InhalesY, PlotWindow.CIRCLE);
            plot.setColor(Color.blue);
            plot.addPoints(ExhalesX, ExhalesY, PlotWindow.CIRCLE);
            plot.show();
            
            getStatisticsAP(InhalesX,InhalesY, ExhalesX, ExhalesY);
            getStatisticsPA(InhalesX,InhalesY, ExhalesX, ExhalesY);
	}
        
        public String getHISdate(String sTitle){
            String miFecha;
            miFecha = sTitle.substring(0, 16);
            miFecha = miFecha.replace(".",":");
            miFecha = miFecha.replace("_"," ");
            //ij.IJ.showMessage(miFecha);
            return miFecha;
        }
        public void getStatisticsAP(float[] InhalesX, float[] InhalesY, float[] ExhalesX, float[] ExhalesY){
            //limitando el rango de X a la región AP (-40º, 140º)
            float[] Cycle = new float[InhalesX.length];
            float[] Amplitude = new float[InhalesX.length+ExhalesX.length];
            float CycleAvg = 0;
            float CycleSD = 0;
            float AmpAvg = 0;
            float AmpSD = 0;
            int CycleIndex = 0;
            int AmpIndex = 0;

            for(int i=0; i<InhalesX.length-1;i++){
                if(InhalesX[i]>=APini && InhalesX[i]<APend){
                    Cycle[CycleIndex]=Math.abs(InhalesX[i+1]-InhalesX[i]);
                    bucle2: 
                    for(int j=0; j<ExhalesX.length;j++){
                        if(ExhalesX[j]>InhalesX[i] && ExhalesX[j]<APend){
                            Amplitude[AmpIndex]=Math.abs(InhalesY[i]-ExhalesY[j]);
                            AmpIndex++;
                            break bucle2;
                        }
                    }
                    CycleIndex++;
                    
                }
            }
            Cycle=(float[])resizeArray(Cycle,CycleIndex);
            Amplitude=(float[])resizeArray(Amplitude,AmpIndex);
            
            CycleAvg = getAvgFloat(Cycle);
            AmpAvg = getAvgFloat(Amplitude);
            CycleSD = getSDFloat(Cycle);
            AmpSD = getSDFloat(Amplitude);
            
            ResultsTable rtAP = new ResultsTable();
            rtAP.incrementCounter();
            rtAP.addLabel("HISdate", sHISdate);
            rtAP.addValue("Cycle_mean", (double)CycleAvg);
            rtAP.addValue("Cycle_SD", (double)CycleSD);
            rtAP.addValue("Amp_mean", (double)AmpAvg);
            rtAP.addValue("Amp_SD", (double)AmpSD);
            rtAP.show("Stats AP");
           
        }
        public void getStatisticsPA(float[] InhalesX, float[] InhalesY, float[] ExhalesX, float[] ExhalesY){
            //limitando el rango de X a la región AP (-40º, 140º)
            float[] Cycle = new float[InhalesX.length];
            float[] Amplitude = new float[InhalesX.length+ExhalesX.length];
            float CycleAvg = 0;
            float CycleSD = 0;
            float AmpAvg = 0;
            float AmpSD = 0;
            int CycleIndex = 0;
            int AmpIndex = 0;
            
            for(int i=0; i<InhalesX.length-1;i++){
                if(InhalesX[i]>=PAini && InhalesX[i]<PAend){
                    Cycle[CycleIndex]=Math.abs(InhalesX[i+1]-InhalesX[i]);
                    bucle2: 
                    for(int j=0; j<ExhalesX.length;j++){
                        if(ExhalesX[j]>InhalesX[i] && ExhalesX[j]<PAend){
                            Amplitude[AmpIndex]=Math.abs(InhalesY[i]-ExhalesY[j]);
                            AmpIndex++;
                            break bucle2;
                        }
                    }
                    CycleIndex++;
                    
                }
            }
            Cycle=(float[])resizeArray(Cycle,CycleIndex);
            Amplitude=(float[])resizeArray(Amplitude,AmpIndex);
            
            CycleAvg = getAvgFloat(Cycle);
            AmpAvg = getAvgFloat(Amplitude);
            CycleSD = getSDFloat(Cycle);
            AmpSD = getSDFloat(Amplitude);
            
            ResultsTable rtPA = new ResultsTable();
            rtPA.incrementCounter();
            rtPA.addLabel("HISdate", sHISdate);
            rtPA.addValue("Cycle_mean", (double)CycleAvg);
            rtPA.addValue("Cycle_SD", (double)CycleSD);
            rtPA.addValue("Amp_mean", (double)AmpAvg);
            rtPA.addValue("Amp_SD", (double)AmpSD);
            rtPA.show("Stats PA");
        }
        public float[] smoothPattern(float[] Y, int n){
            //accion de suavizado de las curvas de orden 'i'
            float[] TmpArray = new float[n];
            float[] Y_tmp = new float[Y.length];
            for(int i=(n-1)/2;i<Y.length-(n-1)/2;i++){
                for(int j=-(n-1)/2;j<=(n-1)/2;j++){
                    TmpArray[j+(n-1)/2]=Y[i+j];
                }
                Y_tmp[i]=getAvgFloat(TmpArray);
            }
            
            return Y_tmp;
        }
        public float[] getInhalesX(float[] X, float[] Y, float[] InhalesY){
            //busco los X que se correspondes a los InhalesY
            float InhalesX[]=new float[InhalesY.length];
            int i_offset=iSmooth;
            for(int k=0;k<InhalesY.length;k++){
                bucle1: 
                for(int i=i_offset+iInhales/2;i<Y.length;i++){
                    if(Y[i]==InhalesY[k]){
                        InhalesX[k]=X[i];
                        i_offset = i;
                        break bucle1;
                    }
                }
            }
            return InhalesX;
        }
        public float[] getInhalesY(float[] X, float[] Y,int n){
            //busco los maximos de la serie X[], Y[]
            int index = 0;
            float[] TmpArray = new float[n];
            float[] InhalesY = new float[Y.length];
            float[] Y_tmp = new float[Y.length];
            for(int i=n;i<Y.length-n;i++){
                for(int j=1;j<=n;j++){
                    TmpArray[j-1]=Y[i-j];
                }
                Y_tmp[i]=getMaximoFloat(TmpArray);
            }
            for(int i=n;i<Y.length-n;i++){
                if(Y_tmp[i-(n/2-1)]==Y_tmp[i] && Y_tmp[i+(n/2-1)]==Y_tmp[i]){
                    InhalesY[index]=Y_tmp[i];
                    index++;
                    i=i+iInhales/2;
                }
            }
            InhalesY=(float[])resizeArray(InhalesY,index);
            //IJ.showMessage("he encontrado " + index + " InhalesY");
            return InhalesY;
        }
        public float[] getExhalesX(float[] X, float[] Y,float[] ExhalesY){
            //busco los X que se correspondes a los ExhalesY
            float ExhalesX[]=new float[ExhalesY.length];
            int i_offset=iSmooth;
            for(int k=0;k<ExhalesY.length;k++){
                bucle1: 
                for(int i=i_offset+iInhales/2;i<Y.length;i++){
                    if(Y[i]==ExhalesY[k]){
                        ExhalesX[k]=X[i];
                        i_offset = i;
                        break bucle1;
                    }
                }
            }
            return ExhalesX;
        }
        public float[] getExhalesY(float[] X, float[] Y,int n){
            //busco los minimos de la serie X[], Y[]
            int index = 0;
            float[] TmpArray = new float[n];
            float[] ExhalesY = new float[Y.length];
            float[] Y_tmp = new float[Y.length];
            for(int i=n;i<Y.length-n;i++){
                for(int j=1;j<=n;j++){
                    TmpArray[j-1]=Y[i-j];
                }
                Y_tmp[i]=getMinimoFloat(TmpArray);
            }
            for(int i=n;i<Y.length-n;i++){
                if(Y_tmp[i-(n/2-2)]==Y_tmp[i] && Y_tmp[i+(n/2-2)]==Y_tmp[i] && Y_tmp[i]!=0){
                    ExhalesY[index]=Y_tmp[i];
                    //IJ.showMessage("he encontrado ExhaleY y vale" + ExhalesY[index]);
                    index++;
                    i=i+iInhales/2;
                }
            }
            ExhalesY=(float[])resizeArray(ExhalesY,index);
            //IJ.showMessage("he encontrado " + index + " ExhalesY");
            return ExhalesY;
        }
        public float[] getRespPatternX(ImagePlus imp2){
            ImageProcessor ip2_ = imp2.getProcessor();
            int width = imp2.getWidth();
            int height = imp2.getHeight();
            int index = 0;
            float X[] = new float[width*height];
            for(int i=0;i<width;i++){
                for(int j=0;j<height;j++){
                    if(ip2_.getPixelValue(i,j)>dUmbral){
                        X[index]=(float)i*360/width;
                        index++;
                    }
                }
            }
            X=(float[]) resizeArray(X,index);
            return X;
        }
        public float[] getRespPatternY(ImagePlus imp2){
            ImageProcessor ip2_ = imp2.getProcessor();
            int width = imp2.getWidth();
            int height = imp2.getHeight();
            int index = 0;
            float Y[] = new float[width*height];
            for(int i=0;i<width;i++){
                for(int j=0;j<height;j++){
                    if(ip2_.getPixelValue(i,j)>dUmbral){
                        Y[index]=j;
                        index++;
                    }
                }
            }
            Y=(float[]) resizeArray(Y,index);
            return Y;
        }
        public ImagePlus getAmsShroud(ImagePlus imp){
		int width = stack.getWidth();
		int height = stack.getHeight();
                int depth = stack.getSize();
                //boolean Artefacts = false;
                
		int dimension = width*height;
                
                short stackslice[] = new short[dimension];
                
                float Ams_Schroud[] = new float[depth*height];
		float Vmax[] = new float[depth*height];
		float FilaTmp[] = new float[width];
                float ColumnTmp[] = new float[height]; //columna del Ams_Schroud para buscar el maximo
                
                stackslice = (short[]) stack.getPixels(1);
                
                for(int i=1;i<=depth;i++) //recorro los slices
		{
                        stackslice = (short[])stack.getPixels(i); //lista de pixeles del slice
			for(int j=0;j<height;j++) //recorro por filas
			{
                            //en cada fila tengo que iniciar el promedio
                            for(int k=0;k<width;k++) //recorro los valores de la fila j-esima y calculo el promedio
                            {
                                FilaTmp[k] = stackslice[j*width+k];
                            }
                            Ams_Schroud[depth*j+(i-1)] = getAvgFloat(FilaTmp, true);//quiero quitar los artefactos en el calculo del Avg
			}
		}
                //busco el valor maximo de cada columna del Ams_Schroud
                for(int i=1;i<=depth;i++)//recorro los slices
                {
                    for(int j=0;j<height;j++) //recorro las filas
                    {
                        ColumnTmp[j]=Ams_Schroud[j*depth+(i-1)];
                    }
                    Vmax[i-1] = getMaximoFloat(ColumnTmp);
                }
                //reescribo el Ams_Schroud normalizado
                for(int i=1;i<=depth;i++)//recorro los slices
                {
                    for(int j=0;j<height;j++) //recorro las filas
                    {
                        Ams_Schroud[depth*j+(i-1)]=Ams_Schroud[depth*j+(i-1)]/Vmax[i-1];
                    }
                }
                ip2 = new FloatProcessor(depth,height, Ams_Schroud,null);
                imp2 = new ImagePlus(sHISdate,ip2);
                return imp2;
        }
	public int getMaximoInt(int[] array){
		int maximo = 0;
		for(int i=0; i<array.length;i++)
		{
			if(array[i]>=maximo)
			{
				maximo=array[i];
			}
		}
		return maximo;
	}
        public float getMaximoFloat(float[] array){
		float maximo = 0;
		for(int i=0; i<array.length;i++)
		{
			if(array[i]>=maximo)
			{
				maximo=array[i];
			}
		}
		return maximo;
	}
	public int getMinimoInt(int[] array){
		int minimo = 0;
		for(int i=0; i<array.length;i++)
		{
			if(array[i]<=minimo)
			{
				minimo=array[i];
			}
		}
		return minimo;
	}
        public float getMinimoFloat(float[] array){
		float minimo = 1000000;
		for(int i=0; i<array.length;i++){
			if(array[i]<=minimo){
				minimo=array[i];
			}
		}
		return minimo;
	}
	public float getAvgFloat(float[] array, boolean isArtefacts){
		float avg = 0;
                int index_ = 0;
                if(isArtefacts){
                    for(int i=0; i<array.length;i++){
                        if(array[i]<iThreshold){
                            avg=(avg+array[i]);
                        }else{
                            index_++;
                        }
                    }
                    avg = (avg/(array.length-index_));
                }else{
                    for(int i=0; i<array.length;i++){
                        avg=(avg+array[i]);  
                    }
                    avg = (avg/array.length);
                }
		if(avg<0) avg=0;
		return avg;
	}
        public float getAvgFloat(float[] array){
		float avg = 0;
                int index_ = 0;
		for(int i=0; i<array.length;i++){
                        avg=(avg+array[i]);  
		}
		avg = (avg/array.length);
		return avg;
	}
        public float getSDFloat(float[] array){
            float tmp = 0;
            float SD = 0;
            float avg = getAvgFloat(array);
            for(int i=0; i<array.length;i++){
                tmp = tmp+(avg-array[i])*(avg-array[i]);
            }
            tmp = tmp/array.length;
            SD = (float)Math.sqrt((double)tmp);
            return SD;
	}
        private static Object resizeArray(Object oldArray, int newSize){
            int oldSize = java.lang.reflect.Array.getLength(oldArray);
            Class elementType = oldArray.getClass().getComponentType();
            Object newArray = java.lang.reflect.Array.newInstance(elementType, newSize);
            int preserveLength = Math.min(oldSize, newSize);
            if (preserveLength > 0){
                System.arraycopy(oldArray, 0, newArray, 0, preserveLength);
            }
            return newArray;
        }

}
