package com.cluster.facelabs.clusterface.Gallery;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cluster.facelabs.clusterface.R;
import com.cluster.facelabs.clusterface.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    GridLayoutManager gridLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gallery_layout);

        recyclerView = findViewById(R.id.recyclerView);
        gridLayoutManager = new GridLayoutManager(getApplicationContext(), 4);
        recyclerView.setLayoutManager(gridLayoutManager);

        Intent intent = getIntent();
        if(intent.getIntExtra("mode", 1) == 1)
            showPeopleGallery();
        else
            showImageGallery(intent.getStringExtra("personIdx"));
    }

    private ArrayList<String> preparePeopleList()
    {
        String resultsPath = Utils.getResultsPath();
        File[] people = new File(resultsPath).listFiles();

        ArrayList<String> imageUrlList = new ArrayList<>();
        if (people != null) {
            for(int i = 0; i < people.length; i++)
            {
                if(people[i].getName().equals("-1"))
                    continue;
                File[] images = people[i].listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().endsWith(".jpg");
                    }
                });
                if (images != null && images.length > 0) {
                    imageUrlList.add(images[0].getAbsolutePath());
                }
            }
        }
        return imageUrlList;
    }

    private ArrayList<String> prepareImageList(String personIdx)
    {
        String personPath = Utils.getResultsPath() + "/" + personIdx;
        File[] images = new File(personPath).listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jpg");
            }
        });
        ArrayList<String> imageUrlList = new ArrayList<>();
        if (images != null) {
            for(File image : images) {
                imageUrlList.add(image.getAbsolutePath());
            }
        }
        return imageUrlList;
    }

    private void showPeopleGallery()
    {
        try {
            ArrayList<String> peopleList = preparePeopleList();
            DataAdapter dataAdapter = new DataAdapter(getApplicationContext(), peopleList, 1);
            recyclerView.setAdapter(dataAdapter);
        } catch (Exception e) {
            Utils.showToast(this, "Error al cargar la galería de personas: " + e.toString());
        }
    }

    private void showImageGallery(String personIdx)
    {
        try {
            ArrayList<String> peopleList = prepareImageList(personIdx);
            DataAdapter dataAdapter = new DataAdapter(getApplicationContext(), peopleList, 2);
            recyclerView.setAdapter(dataAdapter);
        } catch (Exception e) {
            Utils.showToast(this, "Error al cargar la galería de imágenes: " + e.toString());
        }
    }
}