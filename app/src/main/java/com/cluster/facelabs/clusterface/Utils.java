package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.cluster.facelabs.clusterface.InferenceHelper.Encoding;

public class Utils {

    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static Bitmap getBitmapFromUri(Context context, Uri uri) throws IOException {
        return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
    }

    public static String saveImage(Bitmap bitmap, String origFileName, String faceIdx) {
        String savedImagePath = null;
        String imageFileName = origFileName + "_" + faceIdx + ".jpg";
        File cropsDir = new File(Utils.getCropsPath());
        boolean success = true;
        if (!cropsDir.exists()) {
            success = cropsDir.mkdirs();
        }

        if (success) {
            File imageFile = new File(cropsDir, imageFileName);
            savedImagePath = imageFile.getAbsolutePath();
            try {
                OutputStream fOut = new FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
                fOut.close();
            } catch (Exception e) {
                Log.e("Utils", "Error al guardar la imagen de rostro: " + e.toString(), e);
            }
        }
        return savedImagePath;
    }

    public static void copyPhotoToInputFolder(Context context, Uri sourceUri) {
        String destPath = Utils.getInputPath() + "/" + sourceUri.getLastPathSegment() + ".jpg";
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            FileUtils.copyInputStreamToFile(inputStream, new File(destPath));
        } catch (Exception e) {
            showToast(context, "Error al copiar foto al espacio de trabajo: " + e.toString());
        }
    }

    public static void createResultsFolder(ClusteringHandler clHandler, HashMap<String, Encoding> Encodings) {
        String resultsDirPath = getResultsPath();
        File resultsDir = new File(resultsDirPath);

        if (resultsDir.exists()) {
            try {
                FileUtils.deleteDirectory(resultsDir);
            } catch (Exception e) {
                Log.e("Utils", "Error al eliminar el directorio de resultados previo: " + e.toString(), e);
                return;
            }
        }

        boolean success = resultsDir.mkdirs();
        if (!success) {
            return;
        }

        int num_clusters = 0;
        if (MainActivity.clusterMethod.equals("DBScan")) {
            num_clusters = clHandler.mDBClusters.size();
        } else if (MainActivity.clusterMethod.equals("KMeans")) {
            num_clusters = clHandler.bestKMeans.size();
        } else {
            return;
        }

        for (int i = -1; i < num_clusters; i++) {
            String clusterDirPath = resultsDirPath + "/" + i;
            File clusterDir = new File(clusterDirPath);
            if (!clusterDir.exists()) {
                clusterDir.mkdirs();
            }

            File noMedia = new File(clusterDirPath + "/.nomedia");
            try {
                noMedia.createNewFile();
            } catch (Exception e) {
                Log.e("Utils", "Error al crear archivo noMedia: " + e.toString(), e);
            }
        }

        String cropsDirPath = getCropsPath();
        Iterator it = Encodings.entrySet().iterator();
        MainActivity.saveResultsProgressBar.setMax(Encodings.size());
        MainActivity.saveResultsProgressBar.setProgress(0);
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            String fileName = pair.getKey().toString();
            Encoding encoding = (Encoding) pair.getValue();

            int clusterIdx = -1;
            if (MainActivity.clusterMethod.equals(MainActivity.dbscan)) {
                clusterIdx = clHandler.getDBScanClusterIdx(encoding);
            } else if (MainActivity.clusterMethod.equals(MainActivity.kmeans)) {
                clusterIdx = clHandler.getKMeansClusterIdx(encoding);
            } else {
                return;
            }

            String sourcePath = cropsDirPath + "/" + fileName;
            String destPath = resultsDirPath + "/" + clusterIdx + "/" + fileName;

            File source = new File(sourcePath);
            File dest = new File(destPath);
            try {
                FileUtils.copyFile(source, dest);
            } catch (Exception e) {
                Log.e("Utils", "Error al copiar archivo al directorio de destino final: " + e.toString(), e);
            }
            MainActivity.saveResultsProgressBar.incrementProgressBy(1);
        }
    }

    public static void saveEncodings(Context context, HashMap<String, Encoding> Encodings) {
        String encPath = getEncodingsPath();
        File file = new File(encPath);
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
            outputStream.writeObject(Encodings);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            showToast(context, "Error al guardar codificaciones serializadas: " + e.toString());
        }
    }

    public static HashMap<String, Encoding> loadEncodings() {
        String encPath = getEncodingsPath();
        File file = new File(encPath);

        if (!file.exists()) {
            return null;
        }

        HashMap<String, Encoding> encodings = null;
        try {
            ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
            encodings = (HashMap<String, Encoding>) inputStream.readObject();
            inputStream.close();
        } catch (Exception e) {
            Log.e("Utils", "Error al cargar codificaciones existentes: " + e.toString(), e);
        }
        return encodings;
    }

    public static String getBasePath() {
        File root = Environment.getExternalStorageDirectory();
        if (root != null) {
            return root.getAbsolutePath() + "/Clusterface";
        }
        return "/sdcard/Clusterface";
    }

    public static String getInputPath() {
        return getBasePath() + "/Input";
    }

    public static String getCropsPath() {
        return getBasePath() + "/Crops";
    }

    public static String getResultsPath() {
        return getBasePath() + "/Results";
    }

    public static String getEncodingsPath() {
        return getBasePath() + "/encodings.enc";
    }

    public static String getModelPath() {
        return getBasePath() + "/sandberg.tflite";
    }

    public static void importModel(Context context, Uri sourceUri) {
        String destPath = getModelPath();
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            FileUtils.copyInputStreamToFile(inputStream, new File(destPath));
            showToast(context, "Modelo TFLite importado con éxito.");
        } catch (Exception e) {
            showToast(context, "Error al importar el archivo del modelo TFLite: " + e.toString());
        }
    }

    public static void createInputAndCropsFolder() {
        File inputDir = new File(getInputPath());
        if (!inputDir.exists()) {
            inputDir.mkdirs();
        }

        File inputNoMedia = new File(getInputPath() + "/.nomedia");
        if (!inputNoMedia.exists()) {
            try {
                inputNoMedia.createNewFile();
            } catch (Exception e) {
                Log.e("Utils", "Error al crear nomedia en carpeta input: " + e.toString(), e);
            }
        }
        File cropsDir = new File(getCropsPath());
        if (!cropsDir.exists()) {
            cropsDir.mkdirs();
        }
        File cropsNoMedia = new File(getCropsPath() + "/.nomedia");
        if (!cropsNoMedia.exists()) {
            try {
                cropsNoMedia.createNewFile();
            } catch (Exception e) {
                Log.e("Utils", "Error al crear nomedia en carpeta crops: " + e.toString(), e);
            }
        }
    }

    public static boolean hasStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }
}