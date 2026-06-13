package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import static com.cluster.facelabs.clusterface.MainActivity.faceDetectModeSwitch;
import static com.cluster.facelabs.clusterface.MainActivity.minFaceSizeSeekbar;

public class FaceHandler {

    int mIdx = -1;
    File[] files;
    int mQueueCounter = 0;
    int mQueueMax = 5;

    private Context mContext;
    FaceDetector mFaceDetector;

    public FaceHandler(Context context){
        mContext = context;

        float minFaceSize = 0.05f * (1 + minFaceSizeSeekbar.getProgress());
        int performanceMode;
        if(faceDetectModeSwitch.isEnabled())
            performanceMode = FaceDetectorOptions.PERFORMANCE_MODE_FAST;
        else
            performanceMode = FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE;

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(performanceMode)
                        .setMinFaceSize(minFaceSize)
                        .build();

        mFaceDetector = FaceDetection.getClient(options);
    }

    private InputImage getFirebaseVisionImage(Uri imagePath, Bitmap bitmap){
        InputImage image = null;
        if(imagePath != null){
            try {
                image = InputImage.fromFilePath(mContext, imagePath);
            } catch (IOException e) {
                Utils.showToast(mContext, "No se pudo crear la imagen de entrada desde la ruta especificada: " + e.toString());
            }
        }else{
            image = InputImage.fromBitmap(bitmap, 0);
        }
        return image;
    }

    private void runFaceRecognition(final Uri imagePath,
                                    final Bitmap bitmap,
                                    final String imageName){
        InputImage image = getFirebaseVisionImage(imagePath, bitmap);
        if (image == null) {
            next();
            return;
        }
        mFaceDetector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                processFaceRecognitionResult(faces, imagePath, bitmap, imageName);
                                next();
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Utils.showToast(mContext, "Detección de rostros fallida en el procesamiento: " + e.toString());
                                next();
                            }
                        });
    }

    private void processFaceRecognitionResult(List<Face> faces,
                                              Uri imagePath, Bitmap bitmap, String imageName){
        if(faces.size() == 0){
            return;
        }

        Bitmap inputImg;
        if(imagePath != null) {
            try {
                inputImg = Utils.getBitmapFromUri(mContext, imagePath);
            } catch (IOException e) {
                Utils.showToast(mContext, "Error al cargar bitmap original: " + e.toString());
                return;
            }
        }else{
            inputImg = bitmap;
        }

        int i = 0;
        for(Face face : faces){
            int top = face.getBoundingBox().top;
            int left = face.getBoundingBox().left;
            int width = face.getBoundingBox().width();
            int height = face.getBoundingBox().height();

            if (top < 0) top = 0;
            if (left < 0) left = 0;
            if (left + width > inputImg.getWidth()) width = inputImg.getWidth() - left;
            if (top + height > inputImg.getHeight()) height = inputImg.getHeight() - top;

            try {
                Bitmap croppedFace = Bitmap.createBitmap(inputImg, left, top, width, height);
                Utils.saveImage(croppedFace, imageName, String.valueOf(i++));
            }catch (IllegalArgumentException e){
                Log.e("FaceHandler", "Error al procesar el recorte de coordenadas: " + e.toString(), e);
            }
        }

        MainActivity.faceProgressbar.incrementProgressBy(1);
    }

    public void getCrops(){
        String inputDirPath = Utils.getInputPath();

        File inputDir = new File(inputDirPath);
        if(!inputDir.exists()) {
            Utils.showToast(mContext, "Directorio de entrada no localizado: " + inputDirPath);
            return;
        }

        files = inputDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !name.equals(".nomedia");
            }
        });
        if(files == null){
            Utils.showToast(mContext, "No se encontraron archivos en la carpeta de entrada.");
            return;
        }

        File cropsDir = new File(Utils.getCropsPath());
        if(cropsDir.exists())
        {
            File[] existingCrops = cropsDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return !name.equals(".nomedia");
                }
            });
            if (existingCrops != null) {
                for(File crop : existingCrops)
                {
                    String cropName = FilenameUtils.removeExtension(crop.getName());
                    if (cropName.contains("_")) {
                        String possibleInputName  = cropName.substring(0, cropName.lastIndexOf('_')) + "." + FilenameUtils.getExtension(crop.getName());
                        File possibleInput = new File(inputDirPath + "/" + possibleInputName);
                        if(!possibleInput.exists())
                            crop.delete();
                    }
                }
            }
        }

        MainActivity.faceQueueProgressbar.setMax(files.length);
        MainActivity.faceQueueProgressbar.setProgress(0);
        MainActivity.faceProgressbar.setMax(files.length);
        MainActivity.faceProgressbar.setProgress(0);

        next();
    }

    private void next(){
        mIdx++;
        if(mIdx >= files.length) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.faceFeedbackText.setText("Detección finalizada de " + files.length + " fotos.");
                }
            });
            return;
        }
        final String fileName = FilenameUtils.removeExtension(files[mIdx].getName());
        final String currentName = files[mIdx].getName();
        final int currentCount = mIdx + 1;
        final int totalCount = files.length;
        final int percentage = (currentCount * 100) / totalCount;

        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                MainActivity.faceFeedbackText.setText("Procesando: " + currentName + "\n" + currentCount + " / " + totalCount + " (" + percentage + "%)");
            }
        });

        String cropCheckName = Utils.getCropsPath() + "/" + fileName + "_0.jpg";
        File cropCheck = new File(cropCheckName);
        if(cropCheck.exists()){
            MainActivity.faceQueueProgressbar.incrementProgressBy(1);
            MainActivity.faceProgressbar.incrementProgressBy(1);
            next();
        }else{
            Glide.with(mContext).asBitmap().load(files[mIdx])
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            runFaceRecognition(null, resource, fileName);
                        }

                        @Override
                        public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        }
                    });
            MainActivity.faceQueueProgressbar.incrementProgressBy(1);
        }
    }
}