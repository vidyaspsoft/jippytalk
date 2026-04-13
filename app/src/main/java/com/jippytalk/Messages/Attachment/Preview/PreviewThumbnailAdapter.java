package com.jippytalk.Messages.Attachment.Preview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jippytalk.R;

import java.util.List;

/**
 * Horizontal thumbnail strip adapter at the bottom of the preview page.
 * Tap a thumbnail to jump to that file in the ViewPager2.
 */
public class PreviewThumbnailAdapter extends RecyclerView.Adapter<PreviewThumbnailAdapter.ThumbViewHolder> {

    private final Context               context;
    private final List<PreviewItem>     items;
    private int                         selectedPosition    =   0;
    private OnThumbnailClickListener    clickListener;

    public interface OnThumbnailClickListener {
        void onThumbnailClick(int position);
    }

    public PreviewThumbnailAdapter(Context context, List<PreviewItem> items) {
        this.context    =   context;
        this.items      =   items;
    }

    public void setClickListener(OnThumbnailClickListener listener) {
        this.clickListener  =   listener;
    }

    public void setSelectedPosition(int position) {
        int old             =   this.selectedPosition;
        this.selectedPosition   =   position;
        notifyItemChanged(old);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ThumbViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_preview_thumbnail, parent, false);
        return new ThumbViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThumbViewHolder holder, int position) {
        PreviewItem item    =   items.get(position);
        boolean isVisual    =   "image".equals(item.contentType) || "video".equals(item.contentType);

        if (isVisual) {
            holder.ivThumb.setVisibility(View.VISIBLE);
            holder.ivDocThumb.setVisibility(View.GONE);
            Glide.with(context)
                    .load(item.uri)
                    .centerCrop()
                    .placeholder(R.drawable.no_profile)
                    .into(holder.ivThumb);
        } else {
            holder.ivThumb.setVisibility(View.GONE);
            holder.ivDocThumb.setVisibility(View.VISIBLE);
        }

        // Selection indicator: white border around selected thumbnail
        if (position == selectedPosition) {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.RECTANGLE);
            border.setCornerRadius(24f);
            border.setStroke(4, Color.WHITE);
            border.setColor(Color.TRANSPARENT);
            holder.vSelectedBorder.setBackground(border);
            holder.vSelectedBorder.setVisibility(View.VISIBLE);
        } else {
            holder.vSelectedBorder.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onThumbnailClick(holder.getBindingAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ThumbViewHolder extends RecyclerView.ViewHolder {
        final ImageView     ivThumb;
        final ImageView     ivDocThumb;
        final View          vSelectedBorder;
        final CardView      cvThumb;

        ThumbViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb             =   itemView.findViewById(R.id.ivThumb);
            ivDocThumb          =   itemView.findViewById(R.id.ivDocThumb);
            vSelectedBorder     =   itemView.findViewById(R.id.vSelectedBorder);
            cvThumb             =   itemView.findViewById(R.id.cvThumb);
        }
    }
}
