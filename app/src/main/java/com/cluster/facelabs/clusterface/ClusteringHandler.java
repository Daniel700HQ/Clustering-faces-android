package com.cluster.facelabs.clusterface;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.cluster.facelabs.clusterface.InferenceHelper.Encoding;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.cluster.facelabs.clusterface.InferenceHelper.DIM_ENCODING;

public class ClusteringHandler {

    public List<Cluster<DoublePoint>> mDBClusters;
    private float mDBScanEps = 7;
    private int mDBScanMinPts = 30;

    String[] fileNames;
    float[][] encodings;
    List<KMeans.Mean> bestKMeans;
    private static final int mClusterIter = 100;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private void getFloatEncodings(HashMap<String, Encoding> Encodings){
        int NUM_ENCODINGS = Encodings.size();
        int DIM_ENCODING = InferenceHelper.DIM_ENCODING;
        fileNames = new String[NUM_ENCODINGS];
        encodings = new float[NUM_ENCODINGS][DIM_ENCODING];
        Iterator it = Encodings.entrySet().iterator();
        int e = 0;
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            String fileName = pair.getKey().toString();
            fileNames[e] = fileName;
            Encoding encoding = (Encoding) pair.getValue();
            System.arraycopy(encoding.enc, 0, encodings[e++], 0, DIM_ENCODING);
        }
    }

    public void KMeansClustering(HashMap<String, Encoding> Encodings){
        getFloatEncodings(Encodings);

        MainActivity.clusteringProgressBar.setMax(mClusterIter);
        MainActivity.clusteringProgressBar.setProgress(0);

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                KMeans kmeans = new KMeans();
                int k = Integer.parseInt(MainActivity.kmeansKText.getText().toString());
                double bestScore = 0;
                bestKMeans = null;

                for(int km = 0; km < mClusterIter; km ++) {
                    List<KMeans.Mean> means = kmeans.predict(k, encodings);
                    double score = KMeans.score(means);
                    if (score > bestScore) {
                        bestKMeans = means;
                        bestScore = score;
                    }
                    final int currentKm = km;
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.clusteringProgressBar.setProgress(currentKm);
                        }
                    });
                }

                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showKMeansOutput();
                    }
                });
            }
        });
    }

    public void showKMeansOutput(){
        int[] clusterSizes = new int[bestKMeans.size()];

        for(int i = 0; i < encodings.length; i++){
            KMeans.Mean nearestMean = KMeans.nearestMean(encodings[i], bestKMeans);
            int clusterIdx = bestKMeans.indexOf(nearestMean);
            clusterSizes[clusterIdx] += 1;
        }

        String clusterOutputString = "Cluster counts : ";
        for(int i = 0; i < bestKMeans.size(); i++)
            clusterOutputString += (clusterSizes[i] + " ");
        MainActivity.clusterResultsText.setText(clusterOutputString);
    }

    int getKMeansClusterIdx(Encoding encoding){
        KMeans.Mean nearestMean = KMeans.nearestMean(encoding.enc, bestKMeans);
        return bestKMeans.indexOf(nearestMean);
    }

    public void DBScanClustering(HashMap<String, Encoding> Encodings){
        List<DoublePoint> dEncodings = new ArrayList<>();
        Iterator it = Encodings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            Encoding encoding = (Encoding) pair.getValue();

            double[] p = new double[DIM_ENCODING];
            for(int i = 0; i < DIM_ENCODING; i++)
                p[i] = encoding.enc[i];
            DoublePoint dbPoint = new DoublePoint(p);
            dEncodings.add(dbPoint);
        }

        mDBScanEps = Float.parseFloat(MainActivity.dBScanEpsText.getText().toString());
        mDBScanMinPts = Integer.parseInt(MainActivity.dBScanMinPtsText.getText().toString());

        DBSCANClusterer<DoublePoint> dbscan = new DBSCANClusterer<>(mDBScanEps, mDBScanMinPts);

        try {
            mDBClusters = dbscan.cluster(dEncodings);
            showDBScanOutput(Encodings.size());
        } catch (Exception e) {
            MainActivity.clusterResultsText.setText("Error en DBScan: " + e.toString());
        }
    }

    void showDBScanOutput(int numPoints){
        int clusteredPhotos = 0;
        String clusterOutputString = "";
        for(int i = 0; i < mDBClusters.size(); i++){
            int csize = mDBClusters.get(i).getPoints().size();
            clusteredPhotos += csize;
            clusterOutputString += (csize + " ");
        }
        int unclusteredPhotos = numPoints - clusteredPhotos;
        clusterOutputString = "Cluster counts : " + String.valueOf(unclusteredPhotos) + " " + clusterOutputString;

        MainActivity.clusterResultsText.setText(clusterOutputString);
    }

    int getDBScanClusterIdx(Encoding encoding){
        double[] p = new double[DIM_ENCODING];
        for(int i = 0; i < DIM_ENCODING; i++)
            p[i] = encoding.enc[i];
        DoublePoint dbpointEncoding = new DoublePoint(p);

        for(int i = 0; i < mDBClusters.size(); i++) {
            List<DoublePoint> clusterPoints = mDBClusters.get(i).getPoints();
            for(int j = 0; j < clusterPoints.size(); j++) {
                double[] pt = clusterPoints.get(j).getPoint();
                boolean equal = true;
                for (int k = 0; k < DIM_ENCODING; k++) {
                    if (pt[k] != p[k]) {
                        equal = false;
                        break;
                    }
                }
                if (equal) {
                    return i;
                }
            }
        }
        return -1;
    }
}