package com.jippytalk.Common;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.jippytalk.R;

/**
 * AttachmentBottomSheet - A reusable bottom sheet dialog for attachment options.
 * Displays options for Photo, Gallery, Video, Video Gallery, Document, Location, Contact, Audio.
 *
 * Usage:
 *   AttachmentBottomSheet.show(context, option -> {
 *       // handle selected option
 *   });
 */
public class AttachmentBottomSheet {

    // ---- Attachment Option Constants ----

    public static final int     OPTION_PHOTO            =   0;
    public static final int     OPTION_GALLERY          =   1;
    public static final int     OPTION_VIDEO            =   2;
    public static final int     OPTION_VIDEO_GALLERY    =   3;
    public static final int     OPTION_DOCUMENT         =   4;
    public static final int     OPTION_LOCATION         =   5;
    public static final int     OPTION_CONTACT          =   6;
    public static final int     OPTION_AUDIO            =   7;

    // -------------------- Show Bottom Sheet Starts Here ---------------------

    /**
     * Shows the attachment bottom sheet dialog with all available options.
     * When an option is clicked, the dialog is dismissed and the callback is invoked.
     *
     * @param context   the context to create the dialog in
     * @param callback  the callback to receive the selected attachment option
     */
    public static void show(Context context, AttachmentOptionCallback callback) {
        BottomSheetDialog   bottomSheetDialog   =   new BottomSheetDialog(context, R.style.TransparentDialog);
        View                view                =   LayoutInflater.from(context).inflate(
                                                    R.layout.bottom_sheet_attachment, null);
        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.setCanceledOnTouchOutside(true);

        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        // ---- Set Click Listeners For Each Option ----

        setOptionClickListener(view, R.id.llAttachPhoto, OPTION_PHOTO, bottomSheetDialog, callback);
        setOptionClickListener(view, R.id.llAttachGallery, OPTION_GALLERY, bottomSheetDialog, callback);
        setOptionClickListener(view, R.id.llAttachVideo, OPTION_VIDEO, bottomSheetDialog, callback);
        setOptionClickListener(view, R.id.llAttachVideoGallery, OPTION_VIDEO_GALLERY, bottomSheetDialog, callback);
        setOptionClickListener(view, R.id.llAttachDocument, OPTION_DOCUMENT, bottomSheetDialog, callback);
        setOptionClickListener(view, R.id.llAttachLocation, OPTION_LOCATION, bottomSheetDialog, callback);
        setOptionClickListener(view, R.id.llAttachContact, OPTION_CONTACT, bottomSheetDialog, callback);
        setOptionClickListener(view, R.id.llAttachAudio, OPTION_AUDIO, bottomSheetDialog, callback);

        bottomSheetDialog.show();
    }

    // -------------------- Helper Methods Starts Here ---------------------

    /**
     * Sets a click listener on a specific option layout within the bottom sheet.
     * Dismisses the dialog and invokes the callback when clicked.
     *
     * @param rootView      the root view of the bottom sheet
     * @param viewId        the resource ID of the option layout
     * @param option        the attachment option constant
     * @param dialog        the bottom sheet dialog to dismiss
     * @param callback      the callback to invoke
     */
    private static void setOptionClickListener(View rootView, int viewId, int option,
                                               BottomSheetDialog dialog, AttachmentOptionCallback callback) {
        LinearLayout    layout  =   rootView.findViewById(viewId);
        if (layout != null) {
            layout.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) {
                    callback.onAttachmentOptionSelected(option);
                }
            });
        }
    }

    // -------------------- Callback Interface ---------------------

    /**
     * Callback interface for attachment option selection.
     * Implement this to handle the selected attachment type.
     */
    public interface AttachmentOptionCallback {
        /**
         * Called when an attachment option is selected from the bottom sheet.
         *
         * @param option the selected option constant (OPTION_PHOTO, OPTION_GALLERY, etc.)
         */
        void onAttachmentOptionSelected(int option);
    }
}
