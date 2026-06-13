package com.cluster.facelabs.clusterface;

import android.Manifest;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cluster.facelabs.clusterface.Gallery.GalleryActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static ProgressBar faceQueueProgressbar;
    public static ProgressBar faceProgressbar;
    public static ProgressBar encodingQueueProgressBar;
    public static ProgressBar encodingProgressBar;
    public static ProgressBar saveResultsProgressBar;
    public static ProgressBar clusteringProgressBar;
    public static ProgressBar cwGraphProgressBar;

    public static EditText dBScanEpsText;
    public static EditText dBScanMinPtsText;
    public static EditText kmeansKText;
    public static EditText cwThreshText;

    public static Spinner clusterTypeSpinner;
    public static String clusterMethod;
    public static final String dbscan = "DBScan";
    public static final String kmeans = "KMeans";
    public static final String cw = "ChineseWhispers";

    private TextView kDesc, epsDesc, minPtsDesc, cwThreshDesc, cwGraphProgressDesc;

    public static TextView clusterResultsText;
    public static TextView faceFeedbackText;
    public static TextView encodingFeedbackText;

    public static SeekBar minFaceSizeSeekbar;
    public static Switch faceDetectModeSwitch;

    TfliteHandler tfliteHandler = null;
    FaceHandler faceHandler = null;
    ClusteringHandler clusteringHandler = null;
    ChineseWhispersHandler cwHandler = null;

    HashMap<String, InferenceHelper.Encoding> Encodings;

    private static final int RC_STORAGE_PERMISSION = 1001;
    private static final int RC_PHOTO_PICKER = 1002;
    private static final int RC_MANAGE_STORAGE = 1003;
    private static final int RC_MODEL_PICKER = 1004;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();

        faceQueueProgressbar = findViewById(R.id.face_queue_pbar);
        faceProgressbar = findViewById(R.id.face_pbar);
        encodingQueueProgressBar = findViewById(R.id.encoding_queue_pbar);
        encodingProgressBar = findViewById(R.id.encoding_pbar);
        saveResultsProgressBar = findViewById(R.id.save_results_pbar);
        clusteringProgressBar = findViewById(R.id.clustering_pbar);
        cwGraphProgressBar = findViewById(R.id.cw_graph_pbar);

        kDesc = findViewById(R.id.kmeans_cluster_count_desc);
        epsDesc = findViewById(R.id.dbscan_eps_desc);
        minPtsDesc = findViewById(R.id.dbscan_min_count_desc);
        cwThreshDesc = findViewById(R.id.cw_threshold_desc);
        cwGraphProgressDesc = findViewById(R.id.cw_graph_pbar_desc);

        dBScanEpsText = findViewById(R.id.dbscan_eps);
        dBScanMinPtsText = findViewById(R.id.dbscan_min_count);
        kmeansKText = findViewById(R.id.kmeans_cluster_count);
        cwThreshText = findViewById(R.id.cw_threshold);

        clusterResultsText = findViewById(R.id.cluster_output_text);
        faceFeedbackText = findViewById(R.id.face_feedback_text);
        encodingFeedbackText = findViewById(R.id.encoding_feedback_text);

        minFaceSizeSeekbar = findViewById(R.id.min_face_size_seekbar);
        faceDetectModeSwitch = findViewById(R.id.face_detect_mode_switch);

        final ArrayList<View> dbscanViews = new ArrayList<>();
        dbscanViews.add(epsDesc);
        dbscanViews.add(dBScanEpsText);
        dbscanViews.add(minPtsDesc);
        dbscanViews.add(dBScanMinPtsText);

        final ArrayList<View> kmeansViews = new ArrayList<>();
        kmeansViews.add(kDesc);
        kmeansViews.add(kmeansKText);

        final ArrayList<View> cwViews = new ArrayList<>();
        cwViews.add(cwThreshDesc);
        cwViews.add(cwThreshText);
        cwViews.add(cwGraphProgressDesc);
        cwViews.add(cwGraphProgressBar);

        clusterTypeSpinner = findViewById(R.id.cluster_type_spinner);
        List<String> categories = new ArrayList<>();
        categories.add(cw);
        categories.add(kmeans);
        categories.add(dbscan);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        clusterTypeSpinner.setAdapter(spinnerAdapter);
        clusterTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                clusterMethod = parent.getItemAtPosition(position).toString();
                if (clusterMethod.equals(dbscan)) {
                    for (int i = 0; i < dbscanViews.size(); i++)
                        dbscanViews.get(i).setVisibility(View.VISIBLE);
                    for (int i = 0; i < kmeansViews.size(); i++)
                        kmeansViews.get(i).setVisibility(View.GONE);
                    for (int i = 0; i < cwViews.size(); i++)
                        cwViews.get(i).setVisibility(View.GONE);
                } else if (clusterMethod.equals(kmeans)) {
                    for (int i = 0; i < dbscanViews.size(); i++)
                        dbscanViews.get(i).setVisibility(View.GONE);
                    for (int i = 0; i < kmeansViews.size(); i++)
                        kmeansViews.get(i).setVisibility(View.VISIBLE);
                    for (int i = 0; i < cwViews.size(); i++)
                        cwViews.get(i).setVisibility(View.GONE);
                } else if (clusterMethod.equals(cw)) {
                    for (int i = 0; i < dbscanViews.size(); i++)
                        dbscanViews.get(i).setVisibility(View.GONE);
                    for (int i = 0; i < kmeansViews.size(); i++)
                        kmeansViews.get(i).setVisibility(View.GONE);
                    for (int i = 0; i < cwViews.size(); i++)
                        cwViews.get(i).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Encodings = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkModelStatus();
    }

    private void checkModelStatus() {
        File modelFile = new File(Utils.getModelPath());
        View encodeButton = findViewById(R.id.get_encodings_button);
        if (encodeButton != null) {
            if (modelFile.exists()) {
                encodeButton.setEnabled(true);
            } else {
                encodeButton.setEnabled(false);
            }
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, RC_MANAGE_STORAGE);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, RC_MANAGE_STORAGE);
                    } catch (Exception ex) {
                        Utils.showToast(this, "Error al solicitar acceso total a archivos: " + ex.toString());
                    }
                }
            } else {
                Utils.createInputAndCropsFolder();
                checkModelStatus();
            }
        } else {
            boolean readPermissionGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            boolean writePermissionGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
            ArrayList<String> permissionsToRequest = new ArrayList<>();
            if (!readPermissionGranted)
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (!writePermissionGranted)
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]),
                        RC_STORAGE_PERMISSION);
            } else {
                Utils.createInputAndCropsFolder();
                checkModelStatus();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Utils.createInputAndCropsFolder();
                checkModelStatus();
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    this.finishAndRemoveTask();
                } else {
                    showSettingsDialog();
                }
            }
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle("Permisos de almacenamiento obligatorios")
                .setMessage("Has denegado los permisos de almacenamiento necesarios. Otórgalos desde ajustes.")
                .setPositiveButton("Ajustes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", MainActivity.this.getPackageName(), null);
                            intent.setData(uri);
                            startActivityForResult(intent, RC_STORAGE_PERMISSION);
                        } catch (Exception e) {
                            Utils.showToast(MainActivity.this, "Error al redirigir a ajustes de la app: " + e.toString());
                        }
                    }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finishAndRemoveTask();
                    }
                }).setCancelable(false).create().show();
    }

    public void selectInputPhotos(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Seleccionar fotos"), RC_PHOTO_PICKER);
    }

    public void importModel(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Seleccionar modelo sandberg.tflite"), RC_MODEL_PICKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_PHOTO_PICKER) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    if (data.getData() == null) {
                        ClipData clipdata = data.getClipData();
                        if (clipdata != null) {
                            for (int i = 0; i < clipdata.getItemCount(); i++) {
                                Uri imageUri = clipdata.getItemAt(i).getUri();
                                Utils.copyPhotoToInputFolder(this, imageUri);
                            }
                        }
                    } else {
                        Uri imageUri = data.getData();
                        Utils.copyPhotoToInputFolder(this, imageUri);
                    }
                } catch (Exception e) {
                    Utils.showToast(this, "Error al procesar la selección de fotos: " + e.toString());
                }
            } else {
                Utils.showToast(this, "Acción de selección cancelada.");
            }
        } else if (requestCode == RC_STORAGE_PERMISSION || requestCode == RC_MANAGE_STORAGE) {
            requestPermissions();
        } else if (requestCode == RC_MODEL_PICKER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Utils.importModel(this, data.getData());
                checkModelStatus();
            } else {
                Utils.showToast(this, "Importación del modelo cancelada.");
            }
        }
    }

    public void getFaces(View view) {
        if (faceHandler == null) {
            faceHandler = new FaceHandler(this);
        }
        faceHandler.getCrops();
    }

    public void getEncodings(View view) {
        if (tfliteHandler == null) {
            tfliteHandler = new TfliteHandler(this, MainActivity.this);
        }
        tfliteHandler.runTfliteInferenceOnAllCrops();
        Encodings = tfliteHandler.mEncodings;
    }

    public void getClusters(View view) {
        if (Encodings == null) {
            Utils.showToast(this, "No existen codificaciones calculadas para agrupar.");
            return;
        }
        if (clusterMethod.equals(cw)) {
            if (cwHandler == null) {
                cwHandler = new ChineseWhispersHandler(this);
            }
            cwHandler.performClustering(Encodings);
        } else {
            if (clusteringHandler == null) {
                clusteringHandler = new ClusteringHandler();
            }

            if (clusterMethod.equals(dbscan)) {
                clusteringHandler.DBScanClustering(Encodings);
            } else if (clusterMethod.equals(kmeans)) {
                clusteringHandler.KMeansClustering(Encodings);
            }
        }
    }

    public void getResults(View view) {
        if (clusterMethod.equals(cw)) {
            if (cwHandler == null) {
                Utils.showToast(this, "No hay agrupaciones para guardar.");
                return;
            }
            cwHandler.saveResults();
        } else {
            if (clusteringHandler == null) {
                Utils.showToast(this, "No hay agrupaciones para guardar.");
                return;
            }
            Utils.createResultsFolder(clusteringHandler, Encodings);
        }
    }

    public void showResults(View view) {
        File resultsFolder = new File(Utils.getResultsPath());
        if (!resultsFolder.exists()) {
            Utils.showToast(this, "No se encontraron resultados previos.");
            return;
        }
        Intent intent = new Intent(this, GalleryActivity.class);
        intent.putExtra("mode", 1);
        startActivity(intent);
    }
}