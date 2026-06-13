package com.cluster.facelabs.clusterface;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.cluster.facelabs.clusterface.InferenceHelper.Encoding;

import org.apache.commons.io.FileUtils;

public class ChineseWhispersHandler {

    float cwThreshold = 30;
    int cwIter = 500;

    private class Edge{
        public int nbr;
        public float weight;
        public Edge(int _nbr, float _weight){
            nbr = _nbr;
            weight = _weight;
        }
    }

    private class Graph{
        ArrayList<Edge>[] adjLists;
        int[] clusters;

        @SuppressWarnings("unchecked")
        public Graph(int N){
            adjLists = new ArrayList[N];
            clusters = new int[N];
            for(int i = 0; i < N; i++){
                adjLists[i] = new ArrayList<>();
                clusters[i] = i;
            }
        }

        public void addEdge(int src, int dest, float weight){
            adjLists[src].add(new Edge(dest, weight));
            adjLists[dest].add(new Edge(src, weight));
        }
    }

    public Graph graph;
    private Context mContext;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public ChineseWhispersHandler(Context context){
        mContext = context;
        graph = null;
    }

    String[] fileNames;
    float[][] encodings;

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

    public void performClustering(HashMap<String, Encoding> Encodings){
        cwThreshold = Float.parseFloat(MainActivity.cwThreshText.getText().toString());

        getFloatEncodings(Encodings);

        final int NUM_ENCODINGS = Encodings.size();

        graph = new Graph(NUM_ENCODINGS);

        MainActivity.cwGraphProgressBar.setMax(NUM_ENCODINGS);
        MainActivity.cwGraphProgressBar.setProgress(0);

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                int DIM_ENCODING = encodings[0].length;

                for(int i = 0; i < NUM_ENCODINGS; i++) {
                    float[] encoding = encodings[i];

                    if(i == NUM_ENCODINGS-1) break;

                    for(int j = i+1; j < NUM_ENCODINGS; j++){
                        float[] nbrEncoding = encodings[j];
                        float dist = 0.0f;
                        for(int k = 0; k < DIM_ENCODING; k++)
                            dist += encoding[k]*nbrEncoding[k];
                        if(dist > cwThreshold){
                            graph.addEdge(i, j, dist);
                        }
                    }
                    final int progress = i;
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.cwGraphProgressBar.setProgress(progress);
                        }
                    });
                }

                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.clusteringProgressBar.setMax(cwIter);
                        MainActivity.clusteringProgressBar.setProgress(0);
                        executeClusteringAlgorithm(NUM_ENCODINGS);
                    }
                });
            }
        });
    }

    private void executeClusteringAlgorithm(final int NUM_ENCODINGS) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for(int ci = 0; ci < cwIter; ci++){
                    for(int i = 0; i < NUM_ENCODINGS; i++) {
                        HashMap<Integer, Float> clusterWeights = new HashMap<>();

                        for(int j = 0; j < graph.adjLists[i].size(); j++){
                            Edge nbrEdge = graph.adjLists[i].get(j);
                            int nbr = nbrEdge.nbr;
                            float nbrWt = nbrEdge.weight;
                            int nbrCluster = graph.clusters[nbr];
                            if(clusterWeights.containsKey(nbrCluster))
                                clusterWeights.put(nbrCluster, clusterWeights.get(nbrCluster) + nbrWt);
                            else
                                clusterWeights.put(nbrCluster, nbrWt);
                        }

                        int maxCluster = -1;
                        float maxClusterWt = 0.0f;
                        Iterator it = clusterWeights.entrySet().iterator();
                        while (it.hasNext()){
                            Map.Entry pair = (Map.Entry)it.next();
                            int clusterId = (Integer) pair.getKey();
                            float clusterWt = (Float) pair.getValue();
                            if(clusterWt > maxClusterWt){
                                maxClusterWt = clusterWt;
                                maxCluster = clusterId;
                            }
                        }
                        if (maxCluster != -1) {
                            graph.clusters[i] = maxCluster;
                        }
                    }

                    final int currentIter = ci;
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.clusteringProgressBar.setProgress(currentIter);
                        }
                    });
                }

                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        printClusterSizes();
                    }
                });
            }
        });
    }

    private void printClusterSizes(){
        HashMap<Integer, Integer> clusterSizes = new HashMap<>();
        for(int i = 0; i < graph.clusters.length; i++){
            if(clusterSizes.containsKey(graph.clusters[i]))
                clusterSizes.put(graph.clusters[i], clusterSizes.get(graph.clusters[i]) + 1);
            else
                clusterSizes.put(graph.clusters[i], 1);
        }

        String clusterOutputString = "Cluster counts : ";
        Iterator it = clusterSizes.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry pair = (Map.Entry)it.next();
            int size = (Integer) pair.getValue();
            clusterOutputString += (String.valueOf(size) + " ");
        }
        MainActivity.clusterResultsText.setText(clusterOutputString);
    }

    public void saveResults(){
        if(graph == null){
            Utils.showToast(mContext, "No existen resultados de agrupamiento para almacenar.");
            return;
        }

        String cropsDirPath = Utils.getCropsPath();
        String resultsDirPath = Utils.getResultsPath();
        File resultsDir = new File(resultsDirPath);

        try {
            FileUtils.deleteDirectory(resultsDir);
        } catch (IOException e) {
            Utils.showToast(mContext, "Error al purgar los resultados almacenados anteriormente: " + e.toString());
        }

        boolean success = resultsDir.mkdirs();
        if(!success){
            Utils.showToast(mContext, "No se pudo generar la carpeta interna de resultados.");
            return;
        }

        for(int i = 0; i < graph.clusters.length; i++)
        {
            File clusterFolder = new File(resultsDirPath + "/" + graph.clusters[i]);
            clusterFolder.mkdirs();
            File noMedia = new File(resultsDirPath+"/"+graph.clusters[i]+"/.nomedia");
            if(!noMedia.exists()) {
                try {
                    noMedia.createNewFile();
                } catch (IOException e) {
                    Log.e("ChineseWhispers", "Error al crear noMedia en clúster: " + e.toString(), e);
                }
            }
        }

        MainActivity.saveResultsProgressBar.setMax(1);
        MainActivity.saveResultsProgressBar.setProgress(0);

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i < graph.clusters.length; i++){
                    String sourcePath = cropsDirPath + "/" + fileNames[i];
                    String destPath = resultsDirPath + "/" + graph.clusters[i] + "/" +  fileNames[i];
                    File source = new File(sourcePath);
                    File dest = new File(destPath);
                    try {
                        FileUtils.copyFile(source, dest);
                    } catch (IOException e) {
                        Log.e("ChineseWhispers", "Error al transferir rostro al directorio final de resultados: " + e.toString(), e);
                    }
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.saveResultsProgressBar.setProgress(1);
                        Utils.showToast(mContext, "Resultados almacenados de forma correcta.");
                    }
                });
            }
        });
    }
}