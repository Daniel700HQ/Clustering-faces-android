package com.cluster.facelabs.clusterface;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TfliteHandler
{
    private Context mContext;
    private Interpreter mTfliteIntepreter;
    private final boolean IS_QUANT_MODEL = false;

    private final int DIM_X = InferenceHelper.DIM_X;
    private final int DIM_Y = InferenceHelper.DIM_Y;
    private final int DIM_Z = InferenceHelper.DIM_Z;
    private final int DIM_ENCODING = InferenceHelper.DIM_ENCODING;

    private final boolean mPreWhiten = true;
    private float mean, std;

    private final int[] mIntValues = new int[DIM_X * DIM_Y];
    private float [][] mFaceEncodingOutput = null;

    public HashMap<String, InferenceHelper.Encoding> mEncodings;
    public HashMap<String, InferenceHelper.Encoding> prevEncodings;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public TfliteHandler(Context context, Activity activity){
        mContext = context;
        initTfliteModel(activity);
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        File modelFile = new File(Utils.getModelPath());
        FileInputStream inputStream = new FileInputStream(modelFile);
        FileChannel fileChannel = inputStream.getChannel();
        MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length());
        fileChannel.close();
        inputStream.close();
        return mappedByteBuffer;
    }

    private void initTfliteModel(Activity activity){
        try {
            mTfliteIntepreter = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Utils.showToast(mContext, "No se pudo cargar el archivo del modelo de red neuronal: " + e.toString());
            return;
        }
        mFaceEncodingOutput = new float[1][DIM_ENCODING];
        prevEncodings = Utils.loadEncodings();
        mEncodings = new HashMap<>();
        Utils.showToast(mContext, "Modelo TFLite externo cargado correctamente.");
    }

    private void findMeanAndStd(){
        int size = DIM_X*DIM_Y*DIM_Z;
        int pixel = 0;
        int sum = 0;
        for (int i = 0; i < DIM_X; ++i) {
            for (int j = 0; j < DIM_Y; ++j) {
                final int val = mIntValues[pixel++];
                sum += Color.red(val);
                sum += Color.green(val);
                sum += Color.blue(val);
            }
        }
        mean = (float)sum/size;

        pixel = 0;
        float var = 0;
        for (int i = 0; i < DIM_X; ++i) {
            for (int j = 0; j < DIM_Y; ++j) {
                final int val = mIntValues[pixel++];
                var += Math.pow(Color.red(val)-mean, 2);
                var += Math.pow(Color.green(val)-mean, 2);
                var += Math.pow(Color.blue(val)-mean, 2);
            }
        }
        var /= size;
        std = (float)Math.sqrt(var);
        std = Math.max(std, 1.f/((float)Math.sqrt(size)));
    }

    private synchronized ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer imgData =
                ByteBuffer.allocateDirect(1 * DIM_Z * DIM_X * DIM_Y * (IS_QUANT_MODEL?1:4));
        imgData.order(ByteOrder.nativeOrder());
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, DIM_X, DIM_Y, true);
        imgData.rewind();
        scaledBitmap.getPixels(mIntValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());

        int pixel = 0;
        if(IS_QUANT_MODEL){
            for (int i = 0; i < DIM_X; ++i) {
                for (int j = 0; j < DIM_Y; ++j) {
                    final int val = mIntValues[pixel++];
                    imgData.put((byte) ((val >> 16) & 0xFF));
                    imgData.put((byte) ((val >> 8) & 0xFF));
                    imgData.put((byte) (val & 0xFF));
                }
            }
        }else{
            mean = 128.0f;
            std = 128.0f;
            if(mPreWhiten)
                findMeanAndStd();
            for (int i = 0; i < DIM_X; ++i) {
                for (int j = 0; j < DIM_Y; ++j) {
                    final int val = mIntValues[pixel++];
                    imgData.putFloat((((val >> 16) & 0xFF) - mean) / std);
                    imgData.putFloat((((val >> 8) & 0xFF) - mean) / std);
                    imgData.putFloat(((val & 0xFF) - mean) / std);
                }
            }
        }
        return imgData;
    }

    private void runTfliteInference(Bitmap bmap, String fileName){
        if(bmap == null){
            return;
        }

        if(mTfliteIntepreter == null) {
            Utils.showToast(mContext,"El intérprete de TensorFlow Lite no se encuentra inicializado.");
            return;
        }

        ByteBuffer imgData;
        try {
            imgData = convertBitmapToByteBuffer(bmap);
        }catch (Exception e) {
            Utils.showToast(mContext, "Error al generar la estructura de bytes para la imagen: " + e.toString());
            return;
        }

        try {
            mTfliteIntepreter.run(imgData, mFaceEncodingOutput);
        }catch (Exception e){
            Utils.showToast(mContext,"Error al procesar la inferencia con el modelo: " + e.toString());
            return;
        }

        mEncodings.put(fileName, new InferenceHelper.Encoding(mFaceEncodingOutput[0]));
    }

    public void runTfliteInferenceOnAllCrops(){
        String cropsDirPath = Utils.getCropsPath();
        File cropsDir = new File(cropsDirPath);

        final File[] files = cropsDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !name.equals(".nomedia");
            }
        });
        if(files == null){
            Utils.showToast(mContext, "No se encontraron rostros recortados para realizar la inferencia.");
            return;
        }

        MainActivity.encodingProgressBar.setMax(files.length + 1);

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < files.length; i++) {
                    final int progressIndex = i;
                    final String fname = files[i].getName();
                    final int currentCount = i + 1;
                    final int totalCount = files.length;
                    final int percentage = (currentCount * 100) / totalCount;

                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.encodingFeedbackText.setText("Codificando: " + fname + "\n" + currentCount + " / " + totalCount + " (" + percentage + "%)");
                        }
                    });

                    if (prevEncodings != null && prevEncodings.containsKey(fname)) {
                        mEncodings.put(fname, prevEncodings.get(fname));
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.encodingProgressBar.setProgress(progressIndex);
                            }
                        });
                        continue;
                    }

                    Bitmap bm = BitmapFactory.decodeFile(files[i].getAbsolutePath());
                    runTfliteInference(bm, files[i].getName());
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.encodingProgressBar.setProgress(progressIndex);
                        }
                    });
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.encodingFeedbackText.setText("Codificación finalizada de " + files.length + " rostros.");
                        Utils.saveEncodings(mContext, mEncodings);
                    }
                });
            }
        });
    }
}