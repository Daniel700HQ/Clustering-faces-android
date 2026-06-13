package com.cluster.facelabs.clusterface.Gallery;

import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.cluster.facelabs.clusterface.R;
import com.cluster.facelabs.clusterface.Utils;

import java.io.File;
import java.util.ArrayList;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {
    private ArrayList<String> imageUrls;
    private Context context;
    private int mode;

    public DataAdapter(Context context, ArrayList<String> imageUrls, int mode) {
        this.context = context;
        this.imageUrls = imageUrls;
        this.mode = mode;
    }

    @Override
    public DataAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.gallery_item_layout, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final int i) {
        if (imageUrls == null || i >= imageUrls.size()) {
            return;
        }
        Glide.with(context).load(imageUrls.get(i)).into(viewHolder.img);

        if(mode == 1) {
            String[] splits = imageUrls.get(i).split("/");
            if (splits.length >= 2) {
                final String personIdx = splits[splits.length-2];
                File clusterFolder = new File(Utils.getResultsPath() + "/" + personIdx);
                File[] listFiles = clusterFolder.listFiles();
                int count = 0;
                if (listFiles != null) {
                    count = listFiles.length - 1;
                }
                if (count < 0) {
                    count = 0;
                }
                viewHolder.txtView.setText(String.valueOf(count) + " photos");

                viewHolder.img.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.image_click));
                            Intent intent = new Intent(v.getContext(), GalleryActivity.class);
                            intent.putExtra("mode", 2);
                            intent.putExtra("personIdx", personIdx);
                            v.getContext().startActivity(intent);
                        } catch (Exception e) {
                            Utils.showToast(context, "Error al procesar la navegación de galería: " + e.toString());
                        }
                    }
                });
            }
        }
        else {
            viewHolder.img.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.image_click));
                        Intent intent = new Intent(v.getContext(), ImageActivity.class);
                        intent.putExtra("cropPath", imageUrls.get(i));
                        v.getContext().startActivity(intent);
                    } catch (Exception e) {
                        Utils.showToast(context, "Error al abrir la visualización de la imagen: " + e.toString());
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        if (imageUrls == null) {
            return 0;
        }
        return imageUrls.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        TextView txtView;

        public ViewHolder(View view) {
            super(view);
            img = view.findViewById(R.id.imageView);
            txtView = view.findViewById(R.id.textView);
        }
    }
}