package com.jippytalk.Messages.Adapter;


import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.SharedPreferences;

import android.content.res.Resources;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Messages.DiffUtil.MessagesDiffUtil;
import com.jippytalk.Messages.Model.MessageModal;
import com.jippytalk.Messages.Utils.MessageUtils;
import com.jippytalk.R;
import com.jippytalk.databinding.ReceivedAudioMessageBinding;
import com.jippytalk.databinding.ReceivedMediaMessageBinding;
import com.jippytalk.databinding.ReceivedMessageLocationBinding;
import com.jippytalk.databinding.ReceivedVideoMessageBinding;
import com.jippytalk.databinding.ReceiverMessageBinding;
import com.jippytalk.databinding.SenderMessageBinding;
import com.jippytalk.databinding.SentAudioMessageBinding;
import com.jippytalk.databinding.SentLocationBinding;
import com.jippytalk.databinding.SentMediaMessageBinding;
import com.jippytalk.databinding.SentVideoMessageBinding;
import com.jippytalk.databinding.SystemMessagesBinding;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class MessageAdapter extends ListAdapter<MessageModal, RecyclerView.ViewHolder> {

    // ---- Transfer State Constants ----

    public static final int     TRANSFER_IDLE           =   0;
    public static final int     TRANSFER_IN_PROGRESS    =   1;
    public static final int     TRANSFER_COMPLETED      =   2;
    public static final int     TRANSFER_FAILED         =   3;
    public static final int     TRANSFER_CANCELLED      =   4;
    public static final int     TRANSFER_WAITING        =   5;    // receiver: needs download

    // ---- Fields ----

    private final Activity                                          context;
    private final int                                               messageTheme;
    private Animation                                               loading_anim;
    private final SharedPreferences                                 sharedPreferences;
    private final OnMessageItemClickedListener                      onItemClickListener;
    private OnMediaTransferClickListener                            mediaTransferClickListener;
    private int                                                     selectedItemPosition    =   -1;
    private final MessageUtils                                      messageUtils;
    private final ConcurrentHashMap<String, TransferState>          transferStates          =   new ConcurrentHashMap<>();
    private volatile HashMap<String, Integer>                      messagePositionIndex    =   new HashMap<>();

    public MessageAdapter(Activity context, OnMessageItemClickedListener onItemClickListener,
                          SharedPreferences sharedPreferences, MessageUtils messageUtils) {
        super(new MessagesDiffUtil());

        this.context                            =   context;
        this.onItemClickListener                =   onItemClickListener;
        this.sharedPreferences                  =   sharedPreferences;
        this.messageUtils                       =   messageUtils;
        messageTheme                            =   sharedPreferences.getInt(SharedPreferenceDetails.MESSAGE_THEME_BACKGROUND,0);

        if (loading_anim == null) {
           // loading_anim  =   AnimationUtils.loadAnimation(context,R.anim.rotation);
        }

        setHasStableIds(true);
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        switch (viewType) {
            case MessagesManager.SENT_MESSAGE -> {
                SenderMessageBinding senderMessageBinding    =   SenderMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new SentMessageViewHolder(senderMessageBinding);
            }
            case MessagesManager.RECEIVED_MESSAGE -> {
                ReceiverMessageBinding receiverMessageBinding   =   ReceiverMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new ReceivedMessageViewHolder(receiverMessageBinding);
            }
            /*case MessagesManager.SENT_LOCATION -> {
                SentLocationBinding sentLocationBinding     =   SentLocationBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new SentLocationMessageHolder(sentLocationBinding);
            }*/
         /*   case MessagesManager.RECEIVED_LOCATION -> {
                ReceivedMessageLocationBinding receivedMessageLocationBinding   =   ReceivedMessageLocationBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new ReceivedLocationMessageHolder(receivedMessageLocationBinding);
            }*/
            case MessagesManager.SENT_MEDIA -> {
                SentMediaMessageBinding sentMediaBinding     =   SentMediaMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new SentMediaViewHolder(sentMediaBinding);
            }
            case MessagesManager.RECEIVED_MEDIA -> {
                ReceivedMediaMessageBinding receivedMediaBinding  =   ReceivedMediaMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new ReceivedMediaViewHolder(receivedMediaBinding);
            }
            case MessagesManager.SENT_VIDEO -> {
                SentVideoMessageBinding sentVideoBinding     =   SentVideoMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new SentVideoViewHolder(sentVideoBinding);
            }
            case MessagesManager.RECEIVED_VIDEO -> {
                ReceivedVideoMessageBinding receivedVideoBinding =   ReceivedVideoMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new ReceivedVideoViewHolder(receivedVideoBinding);
            }
            case MessagesManager.SENT_AUDIO -> {
                SentAudioMessageBinding sentAudioBinding     =   SentAudioMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new SentAudioViewHolder(sentAudioBinding);
            }
            case MessagesManager.RECEIVED_AUDIO -> {
                ReceivedAudioMessageBinding receivedAudioBinding =   ReceivedAudioMessageBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new ReceivedAudioViewHolder(receivedAudioBinding);
            }
            case MessagesManager.SYSTEM_MESSAGE_TYPE -> {
                SystemMessagesBinding systemMessagesBinding   =   SystemMessagesBinding.
                        inflate(LayoutInflater.from(context), parent, false);
                return new SystemMessageViewHolder(systemMessagesBinding);
            }
            default -> throw new IllegalArgumentException("Invalid view type");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

        MessageModal messageModal   =   getItem(position);

        switch (holder.getItemViewType()) {
            case MessagesManager.SENT_MESSAGE               -> ((SentMessageViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.RECEIVED_MESSAGE           -> ((ReceivedMessageViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.SENT_LOCATION              -> ((SentLocationMessageHolder) holder).bind(
                                                                messageModal,position);
            case MessagesManager.RECEIVED_LOCATION          -> ((ReceivedLocationMessageHolder) holder).bind(
                                                                messageModal,position);
            case MessagesManager.SENT_MEDIA                 -> ((SentMediaViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.RECEIVED_MEDIA             -> ((ReceivedMediaViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.SENT_VIDEO                 -> ((SentVideoViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.RECEIVED_VIDEO             -> ((ReceivedVideoViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.SENT_AUDIO                 -> ((SentAudioViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.RECEIVED_AUDIO             -> ((ReceivedAudioViewHolder) holder).bind(
                                                                messageModal, position, getCurrentList());
            case MessagesManager.SYSTEM_MESSAGE_TYPE        ->  ((SystemMessageViewHolder) holder).bind(messageModal);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("STATUS")) {
            MessageModal messageModal = getItem(position);
            int status = messageModal.getMessageStatus();

            // Update tick icon for all sent types
            if (holder instanceof SentMessageViewHolder sentHolder) {
                setStatusIcon(status, sentHolder.senderMessageBinding.ivSeenImage);
                // Hide retry icon once synced/delivered/seen
                boolean needsRetry = status == MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER
                        || status == MessagesManager.MESSAGE_SEND_FAILED;
                sentHolder.senderMessageBinding.ivRetryMessage.setVisibility(
                        needsRetry ? VISIBLE : GONE);
            } else if (holder instanceof SentMediaViewHolder sentMediaHolder) {
                setStatusIcon(status, sentMediaHolder.sentMediaBinding.ivSeenImage);
                // Hide document pill retry + spinner once synced
                boolean synced = status != MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER
                        && status != MessagesManager.MESSAGE_SEND_FAILED;
                if (synced) {
                    sentMediaHolder.sentMediaBinding.ivPillRetry.setVisibility(GONE);
                    sentMediaHolder.sentMediaBinding.pbPillUpload.setVisibility(GONE);
                }
            } else if (holder instanceof SentVideoViewHolder sentVideoHolder) {
                setStatusIcon(status, sentVideoHolder.sentVideoBinding.ivSeenImage);
            } else if (holder instanceof SentAudioViewHolder sentAudioHolder) {
                setStatusIcon(status, sentAudioHolder.sentAudioBinding.ivSeenImage);
            }
        } else if (!payloads.isEmpty() && payloads.contains("PROGRESS")) {
            MessageModal    messageModal    =   getItem(position);
            String          messageId       =   messageModal.getMessageId();
            TransferState   state           =   transferStates.get(messageId);

            if (state != null) {
                if (holder instanceof SentMediaViewHolder sentMediaHolder) {
                    bindSentMediaTransferState(sentMediaHolder.sentMediaBinding, messageId, state);
                } else if (holder instanceof SentVideoViewHolder sentVideoHolder) {
                    bindSentVideoTransferState(sentVideoHolder.sentVideoBinding, messageId, state);
                } else if (holder instanceof SentAudioViewHolder sentAudioHolder) {
                    bindSentAudioTransferState(sentAudioHolder.sentAudioBinding, messageId, state);
                } else if (holder instanceof ReceivedMediaViewHolder receivedMediaHolder) {
                    bindReceivedMediaTransferState(receivedMediaHolder.receivedMediaBinding, messageId, state);
                } else if (holder instanceof ReceivedVideoViewHolder receivedVideoHolder) {
                    bindReceivedVideoTransferState(receivedVideoHolder.receivedVideoBinding, messageId, state);
                } else if (holder instanceof ReceivedAudioViewHolder receivedAudioHolder) {
                    bindReceivedAudioTransferState(receivedAudioHolder.receivedAudioBinding, messageId, state);
                }
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public int getItemViewType(int position) {
        MessageModal    message     =   getItem(position);
        int             direction   =   message.getMessageDirection();
        int             type        =   message.getMessageType();
        int             isReply     =   message.isReply();

        // System message
        if (direction == MessagesManager.SYSTEM_GIVEN_MESSAGE) {
            return MessagesManager.SYSTEM_MESSAGE_TYPE;
        }

        // Sent messages
        if (direction == MessagesManager.SENT_MESSAGE) {
            if (type == MessagesManager.LOCATION_MESSAGE)   return MessagesManager.SENT_LOCATION;
            if (type == MessagesManager.IMAGE_MESSAGE
                    || type == MessagesManager.DOCUMENT_MESSAGE
                    || type == MessagesManager.CONTACT_MESSAGE) return MessagesManager.SENT_MEDIA;
            if (type == MessagesManager.VIDEO_MESSAGE)      return MessagesManager.SENT_VIDEO;
            if (type == MessagesManager.AUDIO_MESSAGE)      return MessagesManager.SENT_AUDIO;
            return MessagesManager.SENT_MESSAGE;
        }

        // Received messages
        if (direction == MessagesManager.RECEIVED_MESSAGE) {
            if (type == MessagesManager.LOCATION_MESSAGE)   return MessagesManager.RECEIVED_LOCATION;
            if (type == MessagesManager.IMAGE_MESSAGE
                    || type == MessagesManager.DOCUMENT_MESSAGE
                    || type == MessagesManager.CONTACT_MESSAGE) return MessagesManager.RECEIVED_MEDIA;
            if (type == MessagesManager.VIDEO_MESSAGE)      return MessagesManager.RECEIVED_VIDEO;
            if (type == MessagesManager.AUDIO_MESSAGE)      return MessagesManager.RECEIVED_AUDIO;
            return MessagesManager.RECEIVED_MESSAGE;
        }

        // Default fallback
        return MessagesManager.SENT_MESSAGE;
    }

    @Override
    public int getItemCount() {
        return super.getItemCount(); // correct way when using ListAdapter
    }

    @Override
    public long getItemId(int position) {
        return getCurrentList().get(position).getMessageId().hashCode();
    }

    public static class SystemMessageViewHolder extends RecyclerView.ViewHolder {

        public final SystemMessagesBinding systemMessagesBinding;

        public SystemMessageViewHolder(@NonNull SystemMessagesBinding systemMessagesBinding) {
            super(systemMessagesBinding.getRoot());
            this.systemMessagesBinding   =   systemMessagesBinding;
        }

        public void bind(MessageModal messageModal) {
            String  message         =   messageModal.getMessage();
            systemMessagesBinding.tvSystemMessage.setText(message);
        }
    }

    public class SentMessageViewHolder extends RecyclerView.ViewHolder {

        public final SenderMessageBinding senderMessageBinding;

        public SentMessageViewHolder(@NonNull SenderMessageBinding senderMessageBinding) {
            super(senderMessageBinding.getRoot());
            this.senderMessageBinding   =   senderMessageBinding;
        }

        public void bind(MessageModal messageModal, int position, List<MessageModal> currentList) {
            String  messageId                   =   messageModal.getMessageId();
            String  message                     =   messageModal.getMessage();
            long    sentTimeStamp               =   messageModal.getTimestamp();
            int     messageStatus               =   messageModal.getMessageStatus();
            int     isStarred                   =   messageModal.getStarredStatus();
            int     isEdited                    =   messageModal.getEditedStatus();
            int     isMessageReplied            =   messageModal.getIsReply();
            String  repliedToText               =   messageModal.getRepliedToMessageText();
            int     repliedToMessageDirection   =   messageModal.getRepliedToMessageDirection();
            String  repliedToMessageSender      =   messageModal.getRepliedToMessageSenderName();

            if (isMessageReplied == MessagesManager.MESSAGE_IS_REPLIED) {
                senderMessageBinding.cvSentMsgToMsgReply.setVisibility(VISIBLE);
                senderMessageBinding.tvMsgSenderName.setVisibility(VISIBLE);
                senderMessageBinding.selectedMsgTxt.setVisibility(VISIBLE);

                int     repliedToType       =   messageModal.getRepliedToMessageType();
                String  repliedToMediaUri   =   messageModal.getRepliedToMediaUri();

                senderMessageBinding.selectedMsgTxt.setText(
                        MessageUtils.getReplyLabel(context, repliedToType, repliedToText));

                if (MessageUtils.isMediaTypeWithThumbnail(repliedToType)
                        && repliedToMediaUri != null && !repliedToMediaUri.isEmpty()) {
                    senderMessageBinding.ivReplyMediaThumbnail.setVisibility(VISIBLE);
                    com.bumptech.glide.Glide.with(context)
                            .load(repliedToMediaUri)
                            .placeholder(R.drawable.no_profile)
                            .into(senderMessageBinding.ivReplyMediaThumbnail);
                } else {
                    senderMessageBinding.ivReplyMediaThumbnail.setVisibility(GONE);
                }

                if (repliedToMessageDirection == MessagesManager.MESSAGE_INCOMING) {
                    senderMessageBinding.tvMsgSenderName.setText(repliedToMessageSender);
                } else {
                    senderMessageBinding.tvMsgSenderName.setText(R.string.you);
                }
            } else {
                senderMessageBinding.cvSentMsgToMsgReply.setVisibility(GONE);
                senderMessageBinding.tvMsgSenderName.setVisibility(GONE);
                senderMessageBinding.selectedMsgTxt.setVisibility(GONE);
                senderMessageBinding.ivReplyMediaThumbnail.setVisibility(GONE);
            }

            checkForFirstMessageOfDay(currentList, position, senderMessageBinding.cvDateDisplay, senderMessageBinding.tvDateDisplay,
                                        senderMessageBinding.llMessageOverview);
            senderMessageBinding.tvMessage.setText(message);

            senderMessageBinding.tvMessageTime.setText(MessageUtils.getTime(sentTimeStamp));
            setStatusIcon(messageStatus, senderMessageBinding.ivSeenImage);
            setSelectedMessageBackground(senderMessageBinding.llSentMessage, position);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) senderMessageBinding.
                                                    llMessageBackgroundView.getLayoutParams();
            if (messageModal.getMessageDirection() - 1 != 0) {
                params.topMargin = dpToPx(5);
            } else {
                params.topMargin = dpToPx(2);
            }

            senderMessageBinding.llMessageBackgroundView.setBackground(messageUtils.getSentMsgBackground(context, messageTheme,
                                                                        senderMessageBinding.cvSentMsgToMsgReply));

            checkIsStarredOrNot(isStarred, senderMessageBinding.ivStarImage);

            if (isEdited    ==  MessagesManager.MESSAGE_EDITED) {
                senderMessageBinding.ivEditedImage.setVisibility(VISIBLE);
            }
            else {
                senderMessageBinding.ivEditedImage.setVisibility(GONE);
            }

            // Show retry icon for text messages stuck at NOT_SYNCED or explicitly FAILED
            if (messageStatus == MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER
                    || messageStatus == MessagesManager.MESSAGE_SEND_FAILED) {
                senderMessageBinding.ivRetryMessage.setVisibility(VISIBLE);
                senderMessageBinding.ivRetryMessage.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) {
                        mediaTransferClickListener.onRetryTextMessage(messageId, message);
                    }
                });
            } else {
                senderMessageBinding.ivRetryMessage.setVisibility(GONE);
            }

            senderMessageBinding.llSentMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onSentMessageLongClick(messageId, messagePosition);
                return true;
            });

            senderMessageBinding.tvMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onSentMessageLongClick(messageId, messagePosition);
                return true;
            });

            senderMessageBinding.cvSentMsgToMsgReply.setOnClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                if (messagePosition != RecyclerView.NO_POSITION) {
                    onItemClickListener.onSentRepliedMessageTextClick(currentList.get(messagePosition).getReplyToMessageId());
                }
            });

            senderMessageBinding.cvSentMsgToMsgReply.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onSentMessageLongClick(messageId, messagePosition);
                return true;
            });
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }


    public class SentLocationMessageHolder extends RecyclerView.ViewHolder {

        public final SentLocationBinding sentLocationBinding;

        public SentLocationMessageHolder(@NonNull SentLocationBinding sentLocationBinding) {
            super(sentLocationBinding.getRoot());
            this.sentLocationBinding    =   sentLocationBinding;
        }

        public void bind(MessageModal messageModal, int position) {
            int     isStarred       =   messageModal.getStarredStatus();
            setSelectedMessageBackground(sentLocationBinding.llSentLocationMessage, position);
            checkIsStarredOrNot(isStarred, sentLocationBinding.ivLocationStar);
        }
    }


    public class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {

        public final ReceiverMessageBinding receiverMessageBinding;

        public ReceivedMessageViewHolder(@NonNull ReceiverMessageBinding receiverMessageBinding) {
            super(receiverMessageBinding.getRoot());
            this.receiverMessageBinding     =   receiverMessageBinding;
        }

        public void bind(MessageModal messageModal,int position, List<MessageModal> currentList) {

            String  messageId                   =   messageModal.getMessageId();
            String  message                     =   messageModal.getMessage();
            long    sentTimeStamp               =   messageModal.getTimestamp();
            int     isStarred                   =   messageModal.getStarredStatus();
            int     isEdited                    =   messageModal.getEditedStatus();
            int     isMessageReplied            =   messageModal.getIsReply();
            String  repliedToText               =   messageModal.getRepliedToMessageText();
            int     repliedToMessageDirection   =   messageModal.getRepliedToMessageDirection();
            String  repliedToMessageSender      =   messageModal.getRepliedToMessageSenderName();


            if (isMessageReplied == MessagesManager.MESSAGE_IS_REPLIED) {
                receiverMessageBinding.cvReceivedMsgToMsgReply.setVisibility(VISIBLE);
                receiverMessageBinding.tvMsgSenderName.setVisibility(VISIBLE);
                receiverMessageBinding.tvSelectedMsgTxt.setVisibility(VISIBLE);

                int     repliedToType       =   messageModal.getRepliedToMessageType();
                String  repliedToMediaUri   =   messageModal.getRepliedToMediaUri();

                receiverMessageBinding.tvSelectedMsgTxt.setText(
                        MessageUtils.getReplyLabel(context, repliedToType, repliedToText));

                if (MessageUtils.isMediaTypeWithThumbnail(repliedToType)
                        && repliedToMediaUri != null && !repliedToMediaUri.isEmpty()) {
                    receiverMessageBinding.ivReplyMediaThumbnail.setVisibility(VISIBLE);
                    com.bumptech.glide.Glide.with(context)
                            .load(repliedToMediaUri)
                            .placeholder(R.drawable.no_profile)
                            .into(receiverMessageBinding.ivReplyMediaThumbnail);
                } else {
                    receiverMessageBinding.ivReplyMediaThumbnail.setVisibility(GONE);
                }

                if (repliedToMessageDirection == MessagesManager.MESSAGE_INCOMING) {
                    receiverMessageBinding.tvMsgSenderName.setText(repliedToMessageSender);
                } else {
                    receiverMessageBinding.tvMsgSenderName.setText(R.string.you);
                }
            } else {
                receiverMessageBinding.cvReceivedMsgToMsgReply.setVisibility(GONE);
                receiverMessageBinding.tvMsgSenderName.setVisibility(GONE);
                receiverMessageBinding.tvSelectedMsgTxt.setVisibility(GONE);
                receiverMessageBinding.ivReplyMediaThumbnail.setVisibility(GONE);
            }

            checkForFirstMessageOfDay(currentList, position, receiverMessageBinding.cvDateDisplay, receiverMessageBinding.tvDateDisplay,
                                        receiverMessageBinding.llMessageOverview);
            setSelectedMessageBackground(receiverMessageBinding.llReceivedMessage, position);

            receiverMessageBinding.tvMessage.setText(message);
            receiverMessageBinding.tvReceivedMessageTime.setText(MessageUtils.getTime(sentTimeStamp));
            checkIsStarredOrNot(isStarred, receiverMessageBinding.ivStarImage);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) receiverMessageBinding.llReceivedMessage.getLayoutParams();
            params.topMargin = dpToPx(5);

            if (isEdited == MessagesManager.MESSAGE_EDITED) {
                receiverMessageBinding.ivReceivedEditedImage.setVisibility(VISIBLE);
            }
            else {
                receiverMessageBinding.ivReceivedEditedImage.setVisibility(GONE);
            }

            receiverMessageBinding.llReceivedMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onReceivedMessageLongClick(messageId, messagePosition);
                return true;
            });

            receiverMessageBinding.tvMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onReceivedMessageLongClick(messageId, messagePosition);
                return true;
            });

            receiverMessageBinding.cvReceivedMsgToMsgReply.setOnClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                if (messagePosition != RecyclerView.NO_POSITION) {
                    String replyToMessageId = currentList.get(messagePosition).getReplyToMessageId();
                    onItemClickListener.onReceivedRepliedMessageTextClick(replyToMessageId);
                }
            });

            receiverMessageBinding.cvReceivedMsgToMsgReply.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onReceivedMessageLongClick(messageId, messagePosition);
                return true;
            });
        }
    }


    public class ReceivedLocationMessageHolder extends RecyclerView.ViewHolder {

       private final ReceivedMessageLocationBinding receivedMessageLocationBinding;

        public ReceivedLocationMessageHolder(@NonNull ReceivedMessageLocationBinding receivedMessageLocationBinding) {
            super(receivedMessageLocationBinding.getRoot());
            this.receivedMessageLocationBinding     =   receivedMessageLocationBinding;
        }

        public void bind(MessageModal messageModal, int position) {
            setSelectedMessageBackground(receivedMessageLocationBinding.llReceivedLocationMessage, position);
            receivedMessageLocationBinding.ivReceivedLocationTime.setText(MessageUtils.getTime(messageModal.getTimestamp()));
        }
    }

    // -------------------- Sent Media ViewHolder Starts Here ---------------------

    public class SentMediaViewHolder extends RecyclerView.ViewHolder {

        public final SentMediaMessageBinding    sentMediaBinding;

        public SentMediaViewHolder(@NonNull SentMediaMessageBinding sentMediaBinding) {
            super(sentMediaBinding.getRoot());
            this.sentMediaBinding   =   sentMediaBinding;
        }

        public void bind(MessageModal messageModal, int position, List<MessageModal> currentList) {
            String  messageId       =   messageModal.getMessageId();
            long    timestamp       =   messageModal.getTimestamp();
            int     messageStatus   =   messageModal.getMessageStatus();
            int     isStarred       =   messageModal.getStarredStatus();
            String  caption         =   messageModal.getCaption();
            String  fileName        =   messageModal.getFileName();
            String  mediaUri        =   messageModal.getMediaUri();
            int     msgType         =   messageModal.getMessageType();
            boolean isDocument      =   msgType == MessagesManager.DOCUMENT_MESSAGE;
            boolean hasLocalFile    =   mediaUri != null && mediaUri.startsWith("/")
                                        && new java.io.File(mediaUri).exists();

            checkForFirstMessageOfDay(currentList, position, sentMediaBinding.cvDateDisplay,
                    sentMediaBinding.tvDateDisplay, sentMediaBinding.llMessageOverview);
            setSelectedMessageBackground(sentMediaBinding.llSentMediaMessage, position);

            if (isDocument) {
                sentMediaBinding.flMediaPreviewContainer.setVisibility(GONE);
                sentMediaBinding.llDocumentPill.setVisibility(VISIBLE);
                sentMediaBinding.tvDocName.setText(
                        fileName != null && !fileName.isEmpty() ? fileName : "(attachment)");
                sentMediaBinding.tvDocSubtitle.setText(
                        buildDocSubtitle(messageModal.getContentSubtype(), messageModal.getFileSize(), mediaUri, hasLocalFile));
                sentMediaBinding.tvFileName.setVisibility(GONE);

                // Show thumbnail preview if available (e.g. PDF first page)
                String thumbUri = messageModal.getThumbnailUri();
                if (thumbUri != null && !thumbUri.isEmpty()) {
                    sentMediaBinding.ivDocThumbnail.setVisibility(VISIBLE);
                    com.bumptech.glide.Glide.with(context)
                            .load(thumbUri.startsWith("/") ? new java.io.File(thumbUri) : thumbUri)
                            .centerCrop()
                            .into(sentMediaBinding.ivDocThumbnail);
                } else if (hasLocalFile && mediaUri != null) {
                    // Try to generate thumbnail on-the-fly for PDFs
                    java.io.File thumbFile = com.jippytalk.Messages.Attachment.ThumbnailGenerator
                            .generateThumbnail(context, android.net.Uri.fromFile(new java.io.File(mediaUri)), "document");
                    if (thumbFile != null && thumbFile.exists()) {
                        sentMediaBinding.ivDocThumbnail.setVisibility(VISIBLE);
                        com.bumptech.glide.Glide.with(context)
                                .load(thumbFile)
                                .centerCrop()
                                .into(sentMediaBinding.ivDocThumbnail);
                    } else {
                        sentMediaBinding.ivDocThumbnail.setVisibility(GONE);
                    }
                } else {
                    sentMediaBinding.ivDocThumbnail.setVisibility(GONE);
                }
            } else {
                sentMediaBinding.llDocumentPill.setVisibility(GONE);
                sentMediaBinding.flMediaPreviewContainer.setVisibility(VISIBLE);
                if (mediaUri != null && !mediaUri.isEmpty()) {
                    sentMediaBinding.ivMediaPreview.setScaleType(
                            android.widget.ImageView.ScaleType.CENTER_CROP);
                    com.bumptech.glide.Glide.with(context)
                            .load(mediaUri)
                            .placeholder(R.drawable.no_profile)
                            .into(sentMediaBinding.ivMediaPreview);
                }
                if (fileName != null && !fileName.isEmpty()) {
                    sentMediaBinding.tvFileName.setVisibility(VISIBLE);
                    sentMediaBinding.tvFileName.setText(fileName);
                } else {
                    sentMediaBinding.tvFileName.setVisibility(GONE);
                }
            }

            // Show caption if available
            if (caption != null && !caption.isEmpty()) {
                sentMediaBinding.tvCaption.setVisibility(VISIBLE);
                sentMediaBinding.tvCaption.setText(caption);
            } else {
                sentMediaBinding.tvCaption.setVisibility(GONE);
            }

            sentMediaBinding.tvMessageTime.setText(MessageUtils.getTime(timestamp));
            setStatusIcon(messageStatus, sentMediaBinding.ivSeenImage);
            checkIsStarredOrNot(isStarred, sentMediaBinding.ivStarImage);

            // Upload state: for documents, show inline pill indicators (spinner/retry).
            // For images/videos, use the big preview overlay stack.
            TransferState   transferState   =   transferStates.get(messageId);
            if (isDocument) {
                // Keep big-preview overlays hidden for documents
                sentMediaBinding.flUploadOverlay.setVisibility(GONE);
                sentMediaBinding.flRetryOverlay.setVisibility(GONE);

                if (transferState != null
                        && (transferState.getState() == TRANSFER_IN_PROGRESS
                            || transferState.getState() == TRANSFER_COMPLETED)) {
                    // Active upload or just completed (waiting for delivery_status) → spinner
                    sentMediaBinding.pbPillUpload.setVisibility(
                            transferState.getState() == TRANSFER_IN_PROGRESS ? VISIBLE : GONE);
                    sentMediaBinding.ivPillRetry.setVisibility(GONE);
                } else if (messageStatus == MessagesManager.MESSAGE_SEND_FAILED
                        || (transferState != null && transferState.getState() == TRANSFER_FAILED)) {
                    // Explicit failure → show retry
                    sentMediaBinding.pbPillUpload.setVisibility(GONE);
                    sentMediaBinding.ivPillRetry.setVisibility(VISIBLE);
                    sentMediaBinding.ivPillRetry.setOnClickListener(v -> {
                        if (mediaTransferClickListener != null) {
                            mediaTransferClickListener.onRetryUpload(messageId);
                        }
                    });
                } else if (messageStatus == MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER
                        && transferState == null) {
                    // Stuck at NOT_SYNCED with NO transfer state at all (app was restarted,
                    // no in-memory record of this upload) → show retry
                    sentMediaBinding.pbPillUpload.setVisibility(GONE);
                    sentMediaBinding.ivPillRetry.setVisibility(VISIBLE);
                    sentMediaBinding.ivPillRetry.setOnClickListener(v -> {
                        if (mediaTransferClickListener != null) {
                            mediaTransferClickListener.onRetryUpload(messageId);
                        }
                    });
                } else {
                    // Synced/delivered/seen → hide both
                    sentMediaBinding.pbPillUpload.setVisibility(GONE);
                    sentMediaBinding.ivPillRetry.setVisibility(GONE);
                }
            } else if (transferState != null) {
                bindSentMediaTransferState(sentMediaBinding, messageId, transferState);
            } else {
                sentMediaBinding.flUploadOverlay.setVisibility(GONE);
                sentMediaBinding.flRetryOverlay.setVisibility(GONE);
            }

            // Tap to open the local copy (sent documents always have a local file).
            if (hasLocalFile
                    && messageStatus != MessagesManager.MESSAGE_SEND_FAILED
                    && messageStatus != MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER) {
                sentMediaBinding.llSentMediaMessage.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) {
                        mediaTransferClickListener.onOpenFile(messageId, mediaUri);
                    }
                });
            } else {
                sentMediaBinding.llSentMediaMessage.setOnClickListener(null);
            }

            sentMediaBinding.llSentMediaMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onSentMessageLongClick(messageId, messagePosition);
                return true;
            });
        }
    }

    // -------------------- Received Media ViewHolder Starts Here ---------------------

    public class ReceivedMediaViewHolder extends RecyclerView.ViewHolder {

        public final ReceivedMediaMessageBinding    receivedMediaBinding;

        public ReceivedMediaViewHolder(@NonNull ReceivedMediaMessageBinding receivedMediaBinding) {
            super(receivedMediaBinding.getRoot());
            this.receivedMediaBinding   =   receivedMediaBinding;
        }

        public void bind(MessageModal messageModal, int position, List<MessageModal> currentList) {
            String  messageId       =   messageModal.getMessageId();
            long    timestamp       =   messageModal.getTimestamp();
            int     isStarred       =   messageModal.getStarredStatus();
            String  caption         =   messageModal.getCaption();
            String  fileName        =   messageModal.getFileName();
            String  mediaUri        =   messageModal.getMediaUri();
            int     msgType         =   messageModal.getMessageType();
            boolean isDocument      =   msgType == MessagesManager.DOCUMENT_MESSAGE;
            boolean isDownloaded    =   mediaUri != null && mediaUri.startsWith("/")
                                        && new java.io.File(mediaUri).exists();

            checkForFirstMessageOfDay(currentList, position, receivedMediaBinding.cvDateDisplay,
                    receivedMediaBinding.tvDateDisplay, receivedMediaBinding.llMessageOverview);
            setSelectedMessageBackground(receivedMediaBinding.llReceivedMediaMessage, position);

            if (isDocument) {
                receivedMediaBinding.flMediaPreviewContainer.setVisibility(GONE);
                receivedMediaBinding.llDocumentPill.setVisibility(VISIBLE);
                receivedMediaBinding.tvDocName.setText(
                        fileName != null && !fileName.isEmpty() ? fileName : "(attachment)");
                receivedMediaBinding.tvDocSubtitle.setText(
                        buildDocSubtitle(messageModal.getContentSubtype(), messageModal.getFileSize(), mediaUri, isDownloaded));
                receivedMediaBinding.tvFileName.setVisibility(GONE);

                // Show thumbnail preview if available
                String thumbUri = messageModal.getThumbnailUri();
                if (thumbUri != null && !thumbUri.isEmpty()) {
                    receivedMediaBinding.ivDocThumbnail.setVisibility(VISIBLE);
                    com.bumptech.glide.Glide.with(context)
                            .load(thumbUri.startsWith("/") ? new java.io.File(thumbUri) : thumbUri)
                            .centerCrop()
                            .into(receivedMediaBinding.ivDocThumbnail);
                } else if (isDownloaded && mediaUri != null) {
                    java.io.File thumbFile = com.jippytalk.Messages.Attachment.ThumbnailGenerator
                            .generateThumbnail(context, android.net.Uri.fromFile(new java.io.File(mediaUri)), "document");
                    if (thumbFile != null && thumbFile.exists()) {
                        receivedMediaBinding.ivDocThumbnail.setVisibility(VISIBLE);
                        com.bumptech.glide.Glide.with(context)
                                .load(thumbFile)
                                .centerCrop()
                                .into(receivedMediaBinding.ivDocThumbnail);
                    } else {
                        receivedMediaBinding.ivDocThumbnail.setVisibility(GONE);
                    }
                } else {
                    receivedMediaBinding.ivDocThumbnail.setVisibility(GONE);
                }
            } else {
                receivedMediaBinding.llDocumentPill.setVisibility(GONE);
                receivedMediaBinding.flMediaPreviewContainer.setVisibility(VISIBLE);
                if (mediaUri != null && !mediaUri.isEmpty()) {
                    receivedMediaBinding.ivMediaPreview.setScaleType(
                            android.widget.ImageView.ScaleType.CENTER_CROP);
                    com.bumptech.glide.Glide.with(context)
                            .load(mediaUri)
                            .placeholder(R.drawable.no_profile)
                            .into(receivedMediaBinding.ivMediaPreview);
                }
                if (fileName != null && !fileName.isEmpty()) {
                    receivedMediaBinding.tvFileName.setVisibility(VISIBLE);
                    receivedMediaBinding.tvFileName.setText(fileName);
                } else {
                    receivedMediaBinding.tvFileName.setVisibility(GONE);
                }
            }

            if (caption != null && !caption.isEmpty()) {
                receivedMediaBinding.tvCaption.setVisibility(VISIBLE);
                receivedMediaBinding.tvCaption.setText(caption);
            } else {
                receivedMediaBinding.tvCaption.setVisibility(GONE);
            }

            receivedMediaBinding.tvMessageTime.setText(MessageUtils.getTime(timestamp));
            checkIsStarredOrNot(isStarred, receivedMediaBinding.ivStarImage);

            // Overlay logic for the download state.
            // Non-document (image/video) → uses the big-preview overlay stack.
            // Document → uses the inline download button inside the compact pill.
            TransferState   transferState   =   transferStates.get(messageId);
            if (isDocument) {
                // Always keep the big-preview overlays hidden for documents
                receivedMediaBinding.flDownloadOverlay.setVisibility(GONE);
                receivedMediaBinding.flDownloadProgressOverlay.setVisibility(GONE);
                receivedMediaBinding.flRetryOverlay.setVisibility(GONE);

                if (transferState != null
                        && transferState.getState() == TRANSFER_IN_PROGRESS) {
                    receivedMediaBinding.ivPillDownload.setVisibility(GONE);
                    receivedMediaBinding.pbPillDownload.setVisibility(VISIBLE);
                } else if (!isDownloaded) {
                    receivedMediaBinding.ivPillDownload.setVisibility(VISIBLE);
                    receivedMediaBinding.pbPillDownload.setVisibility(GONE);
                    receivedMediaBinding.ivPillDownload.setOnClickListener(v -> {
                        if (mediaTransferClickListener != null) {
                            mediaTransferClickListener.onDownload(messageId);
                        }
                    });
                } else {
                    receivedMediaBinding.ivPillDownload.setVisibility(GONE);
                    receivedMediaBinding.pbPillDownload.setVisibility(GONE);
                }
            } else if (transferState != null) {
                bindReceivedMediaTransferState(receivedMediaBinding, messageId, transferState);
            } else {
                receivedMediaBinding.flDownloadOverlay.setVisibility(GONE);
                receivedMediaBinding.flDownloadProgressOverlay.setVisibility(GONE);
                receivedMediaBinding.flRetryOverlay.setVisibility(GONE);
            }

            // Tap to open an already-downloaded file.
            if (isDownloaded) {
                receivedMediaBinding.llReceivedMediaMessage.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) {
                        mediaTransferClickListener.onOpenFile(messageId, mediaUri);
                    }
                });
            } else {
                receivedMediaBinding.llReceivedMediaMessage.setOnClickListener(null);
            }

            receivedMediaBinding.llReceivedMediaMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onReceivedMessageLongClick(messageId, messagePosition);
                return true;
            });
        }
    }

    // -------------------- Sent Video ViewHolder Starts Here ---------------------

    public class SentVideoViewHolder extends RecyclerView.ViewHolder {

        public final SentVideoMessageBinding    sentVideoBinding;

        public SentVideoViewHolder(@NonNull SentVideoMessageBinding sentVideoBinding) {
            super(sentVideoBinding.getRoot());
            this.sentVideoBinding   =   sentVideoBinding;
        }

        public void bind(MessageModal messageModal, int position, List<MessageModal> currentList) {
            String  messageId       =   messageModal.getMessageId();
            long    timestamp       =   messageModal.getTimestamp();
            int     messageStatus   =   messageModal.getMessageStatus();
            int     isStarred       =   messageModal.getStarredStatus();
            String  caption         =   messageModal.getCaption();
            String  mediaUri        =   messageModal.getMediaUri();
            long    duration        =   messageModal.getMediaDuration();

            checkForFirstMessageOfDay(currentList, position, sentVideoBinding.cvDateDisplay,
                    sentVideoBinding.tvDateDisplay, sentVideoBinding.llMessageOverview);
            setSelectedMessageBackground(sentVideoBinding.llSentVideoMessage, position);

            // Load video thumbnail using Glide
            if (mediaUri != null && !mediaUri.isEmpty()) {
                com.bumptech.glide.Glide.with(context)
                        .load(mediaUri)
                        .placeholder(R.drawable.no_profile)
                        .into(sentVideoBinding.ivVideoThumbnail);
            }

            sentVideoBinding.tvDuration.setText(MessageUtils.formatDuration(duration));

            if (caption != null && !caption.isEmpty()) {
                sentVideoBinding.tvCaption.setVisibility(VISIBLE);
                sentVideoBinding.tvCaption.setText(caption);
            } else {
                sentVideoBinding.tvCaption.setVisibility(GONE);
            }

            sentVideoBinding.tvMessageTime.setText(MessageUtils.getTime(timestamp));
            setStatusIcon(messageStatus, sentVideoBinding.ivSeenImage);
            checkIsStarredOrNot(isStarred, sentVideoBinding.ivStarImage);

            TransferState   transferState   =   transferStates.get(messageId);
            if (transferState != null) {
                bindSentVideoTransferState(sentVideoBinding, messageId, transferState);
            } else {
                sentVideoBinding.flUploadOverlay.setVisibility(GONE);
                sentVideoBinding.flRetryOverlay.setVisibility(GONE);
            }

            sentVideoBinding.llSentVideoMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onSentMessageLongClick(messageId, messagePosition);
                return true;
            });
        }
    }

    // -------------------- Received Video ViewHolder Starts Here ---------------------

    public class ReceivedVideoViewHolder extends RecyclerView.ViewHolder {

        public final ReceivedVideoMessageBinding    receivedVideoBinding;

        public ReceivedVideoViewHolder(@NonNull ReceivedVideoMessageBinding receivedVideoBinding) {
            super(receivedVideoBinding.getRoot());
            this.receivedVideoBinding   =   receivedVideoBinding;
        }

        public void bind(MessageModal messageModal, int position, List<MessageModal> currentList) {
            String  messageId       =   messageModal.getMessageId();
            long    timestamp       =   messageModal.getTimestamp();
            int     isStarred       =   messageModal.getStarredStatus();
            String  caption         =   messageModal.getCaption();
            String  mediaUri        =   messageModal.getMediaUri();
            long    duration        =   messageModal.getMediaDuration();

            checkForFirstMessageOfDay(currentList, position, receivedVideoBinding.cvDateDisplay,
                    receivedVideoBinding.tvDateDisplay, receivedVideoBinding.llMessageOverview);
            setSelectedMessageBackground(receivedVideoBinding.llReceivedVideoMessage, position);

            if (mediaUri != null && !mediaUri.isEmpty()) {
                com.bumptech.glide.Glide.with(context)
                        .load(mediaUri)
                        .placeholder(R.drawable.no_profile)
                        .into(receivedVideoBinding.ivVideoThumbnail);
            }

            receivedVideoBinding.tvDuration.setText(MessageUtils.formatDuration(duration));

            if (caption != null && !caption.isEmpty()) {
                receivedVideoBinding.tvCaption.setVisibility(VISIBLE);
                receivedVideoBinding.tvCaption.setText(caption);
            } else {
                receivedVideoBinding.tvCaption.setVisibility(GONE);
            }

            receivedVideoBinding.tvMessageTime.setText(MessageUtils.getTime(timestamp));
            checkIsStarredOrNot(isStarred, receivedVideoBinding.ivStarImage);

            TransferState   transferState   =   transferStates.get(messageId);
            if (transferState != null) {
                bindReceivedVideoTransferState(receivedVideoBinding, messageId, transferState);
            } else {
                receivedVideoBinding.flDownloadOverlay.setVisibility(GONE);
                receivedVideoBinding.flDownloadProgressOverlay.setVisibility(GONE);
                receivedVideoBinding.flRetryOverlay.setVisibility(GONE);
            }

            receivedVideoBinding.llReceivedVideoMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onReceivedMessageLongClick(messageId, messagePosition);
                return true;
            });
        }
    }

    // -------------------- Sent Audio ViewHolder Starts Here ---------------------

    public class SentAudioViewHolder extends RecyclerView.ViewHolder {

        public final SentAudioMessageBinding    sentAudioBinding;

        public SentAudioViewHolder(@NonNull SentAudioMessageBinding sentAudioBinding) {
            super(sentAudioBinding.getRoot());
            this.sentAudioBinding   =   sentAudioBinding;
        }

        public void bind(MessageModal messageModal, int position, List<MessageModal> currentList) {
            String  messageId       =   messageModal.getMessageId();
            long    timestamp       =   messageModal.getTimestamp();
            int     messageStatus   =   messageModal.getMessageStatus();
            int     isStarred       =   messageModal.getStarredStatus();
            long    duration        =   messageModal.getMediaDuration();

            checkForFirstMessageOfDay(currentList, position, sentAudioBinding.cvDateDisplay,
                    sentAudioBinding.tvDateDisplay, sentAudioBinding.llMessageOverview);
            setSelectedMessageBackground(sentAudioBinding.llSentAudioMessage, position);

            sentAudioBinding.tvDuration.setText(MessageUtils.formatDuration(duration));
            sentAudioBinding.tvMessageTime.setText(MessageUtils.getTime(timestamp));
            setStatusIcon(messageStatus, sentAudioBinding.ivSeenImage);
            checkIsStarredOrNot(isStarred, sentAudioBinding.ivStarImage);

            TransferState   transferState   =   transferStates.get(messageId);
            if (transferState != null) {
                bindSentAudioTransferState(sentAudioBinding, messageId, transferState);
            } else {
                sentAudioBinding.progressBarUpload.setVisibility(GONE);
                sentAudioBinding.ivCancelUpload.setVisibility(GONE);
                sentAudioBinding.ivRetryUpload.setVisibility(GONE);
            }

            sentAudioBinding.llSentAudioMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onSentMessageLongClick(messageId, messagePosition);
                return true;
            });

            // TODO: wire ivPlayPause click to play/pause audio
            // TODO: wire seekBarAudio to audio progress
        }
    }

    // -------------------- Received Audio ViewHolder Starts Here ---------------------

    public class ReceivedAudioViewHolder extends RecyclerView.ViewHolder {

        public final ReceivedAudioMessageBinding    receivedAudioBinding;

        public ReceivedAudioViewHolder(@NonNull ReceivedAudioMessageBinding receivedAudioBinding) {
            super(receivedAudioBinding.getRoot());
            this.receivedAudioBinding   =   receivedAudioBinding;
        }

        public void bind(MessageModal messageModal, int position, List<MessageModal> currentList) {
            String  messageId       =   messageModal.getMessageId();
            long    timestamp       =   messageModal.getTimestamp();
            int     isStarred       =   messageModal.getStarredStatus();
            long    duration        =   messageModal.getMediaDuration();

            checkForFirstMessageOfDay(currentList, position, receivedAudioBinding.cvDateDisplay,
                    receivedAudioBinding.tvDateDisplay, receivedAudioBinding.llMessageOverview);
            setSelectedMessageBackground(receivedAudioBinding.llReceivedAudioMessage, position);

            receivedAudioBinding.tvDuration.setText(MessageUtils.formatDuration(duration));
            receivedAudioBinding.tvMessageTime.setText(MessageUtils.getTime(timestamp));
            checkIsStarredOrNot(isStarred, receivedAudioBinding.ivStarImage);

            TransferState   transferState   =   transferStates.get(messageId);
            if (transferState != null) {
                bindReceivedAudioTransferState(receivedAudioBinding, messageId, transferState);
            } else {
                receivedAudioBinding.ivDownload.setVisibility(GONE);
                receivedAudioBinding.progressBarDownload.setVisibility(GONE);
                receivedAudioBinding.ivRetryDownload.setVisibility(GONE);
                receivedAudioBinding.ivPlayPause.setVisibility(VISIBLE);
            }

            receivedAudioBinding.llReceivedAudioMessage.setOnLongClickListener(view -> {
                int messagePosition =   getBindingAdapterPosition();
                onItemClickListener.onReceivedMessageLongClick(messageId, messagePosition);
                return true;
            });

            // TODO: wire ivPlayPause click to play/pause audio
            // TODO: wire seekBarAudio to audio progress
        }
    }

    private void setStatusIcon(int messageStatus, ImageView imageView) {
        switch (messageStatus) {
            case MessagesManager.MESSAGE_DELIVERED, MessagesManager.MESSAGE_DELIVERED_LOCALLY -> {
                imageView.setImageResource(R.drawable.message_not_seen);   // ✓✓ grey
            }
            case MessagesManager.MESSAGE_SEEN, MessagesManager.MESSAGE_SEEN_LOCALLY -> {
                imageView.clearAnimation();
                imageView.setImageResource(R.drawable.message_seen);       // ✓✓ blue
            }
            case MessagesManager.MESSAGE_SYNCED_WITH_SERVER, MessagesManager.UNKNOWN_NUMBER_MESSAGE -> {
                imageView.clearAnimation();
                imageView.setImageResource(R.drawable.message_sent);       // ✓ grey
            }
            case MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER -> {
                imageView.setImageResource(R.drawable.ic_baseline_access_time_24);  // ⏱ clock
            }
            case MessagesManager.MESSAGE_SEND_FAILED -> {
                imageView.clearAnimation();
                imageView.setImageResource(R.drawable.alert);              // ⚠ red error
            }
        }
    }

    private void checkIsStarredOrNot(int isStarred, ImageView imageView) {
        if (isStarred == MessagesManager.MESSAGE_IS_STARRED) {
            imageView.setVisibility(VISIBLE);
        }
        else {
            imageView.setVisibility(GONE);
        }
    }

    /**
     * Builds the subtitle text shown under a document's filename in the pill,
     * WhatsApp-style: "18 KB · PDF" when the size is known, or just "PDF" before
     * download completes.
     *
     * @param contentSubtype e.g. "pdf", "jpeg", "docx"
     * @param mediaUri       local file path when downloaded, else s3_key / blank
     * @param hasLocalFile   true when mediaUri points to an existing local file
     */
    private String buildDocSubtitle(String contentSubtype, long fileSize,
                                    String mediaUri, boolean hasLocalFile) {
        String  typeLabel = contentSubtype != null && !contentSubtype.isEmpty()
                ? contentSubtype.toUpperCase(java.util.Locale.US) : "";
        String  sizeLabel = "";
        // Prefer the stored fileSize from metadata; fall back to local file if available
        if (fileSize > 0) {
            sizeLabel = formatByteSize(fileSize);
        } else if (hasLocalFile && mediaUri != null) {
            long bytes = new java.io.File(mediaUri).length();
            if (bytes > 0) sizeLabel = formatByteSize(bytes);
        }
        if (!sizeLabel.isEmpty() && !typeLabel.isEmpty()) return sizeLabel + " · " + typeLabel;
        if (!typeLabel.isEmpty())                         return typeLabel;
        if (!sizeLabel.isEmpty())                         return sizeLabel;
        return "Document";
    }

    /** Formats a byte count as "123 B" / "45 KB" / "3.2 MB". */
    private String formatByteSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void setSelectedMessageBackground(LinearLayout layout, int position) {
        if (selectedItemPosition == position) {
            layout.setBackgroundColor(context.getResources().getColor(R.color.teal_200, Resources.getSystem().newTheme()));
        }
        else {
            layout.setBackgroundColor(0);
        }
    }

    private void checkForFirstMessageOfDay(List<MessageModal> currentList, int position, CardView cardView,
                                           TextView textView, View layoutToAdjust) {
        ViewGroup.MarginLayoutParams params         = (ViewGroup.MarginLayoutParams) layoutToAdjust.getLayoutParams();
        ViewGroup.MarginLayoutParams textViewParams = (ViewGroup.MarginLayoutParams) cardView.getLayoutParams();

        if (MessageUtils.isFirstMessageOfDay(currentList, position)) {
            String messageDateLabel = MessageUtils.getDateLabel(currentList.get(position).getTimestamp());
            cardView.setVisibility(VISIBLE);
            textView.setText(messageDateLabel);
            textViewParams.topMargin    =   dpToPx(12);
            params.topMargin            =   dpToPx(12);
        } else {
            cardView.setVisibility(GONE);
            params.topMargin = dpToPx(2);
        }

        layoutToAdjust.setLayoutParams(params);
        cardView.setLayoutParams(textViewParams);
    }

    public void resetSelectedItem() {
        if (selectedItemPosition != RecyclerView.NO_POSITION) {
            selectedItemPosition = RecyclerView.NO_POSITION;
        }
    }

    @Override
    public void onCurrentListChanged(@NonNull List<MessageModal> previousList, @NonNull List<MessageModal> currentList) {
        super.onCurrentListChanged(previousList, currentList);
        rebuildPositionIndex(currentList);
    }

    /**
     * Rebuilds the messageId -> position index for O(1) lookups.
     * Called automatically whenever the list changes via submitList().
     */
    private void rebuildPositionIndex(List<MessageModal> list) {
        HashMap<String, Integer>    newIndex    =   new HashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            newIndex.put(list.get(i).getMessageId(), i);
        }
        messagePositionIndex    =   newIndex;
    }

    /**
     * Finds the adapter position for a messageId using the O(1) index.
     *
     * @param messageId the message identifier
     * @return the position, or -1 if not found
     */
    public int findMessagePositionById(String messageId) {
        Integer position    =   messagePositionIndex.get(messageId);
        return position != null ? position : -1;
    }

    // -------------------- Transfer State Management Starts Here ---------------------

    /**
     * Sets the media transfer click listener for cancel/retry/download actions.
     *
     * @param listener the listener to handle media transfer actions
     */
    public void setMediaTransferClickListener(OnMediaTransferClickListener listener) {
        this.mediaTransferClickListener     =   listener;
    }

    /**
     * Updates the transfer progress for a specific message and triggers a lightweight UI update.
     * Call this from the upload/download callbacks on the main thread.
     *
     * @param messageId     the message identifier
     * @param percentage    the progress from 0 to 100
     * @param state         the transfer state constant (TRANSFER_IN_PROGRESS, TRANSFER_COMPLETED, etc.)
     */
    public void updateTransferProgress(String messageId, int percentage, int state) {
        transferStates.put(messageId, new TransferState(state, percentage));
        int position    =   findMessagePositionById(messageId);
        if (position != -1) {
            notifyItemChanged(position, "PROGRESS");
        }
    }

    /**
     * Marks the transfer as completed and removes the state tracking.
     *
     * @param messageId the message identifier
     */
    public void markTransferComplete(String messageId) {
        transferStates.put(messageId, new TransferState(TRANSFER_COMPLETED, 100));
        int position    =   findMessagePositionById(messageId);
        if (position != -1) {
            notifyItemChanged(position, "PROGRESS");
        }
    }

    /**
     * Marks the transfer as failed so the retry button is shown.
     *
     * @param messageId the message identifier
     */
    public void markTransferFailed(String messageId) {
        transferStates.put(messageId, new TransferState(TRANSFER_FAILED, 0));
        int position    =   findMessagePositionById(messageId);
        if (position != -1) {
            notifyItemChanged(position, "PROGRESS");
        }
    }

    /**
     * Sets the initial state for a receiver-side message that needs downloading.
     *
     * @param messageId the message identifier
     */
    public void markAsWaitingForDownload(String messageId) {
        transferStates.put(messageId, new TransferState(TRANSFER_WAITING, 0));
        int position    =   findMessagePositionById(messageId);
        if (position != -1) {
            notifyItemChanged(position, "PROGRESS");
        }
    }

    /**
     * Clears the transfer state for a message (after completion or when no longer relevant).
     *
     * @param messageId the message identifier
     */
    public void clearTransferState(String messageId) {
        transferStates.remove(messageId);
    }

    // -------------------- Sender Transfer State Bind Helpers ---------------------

    private void bindSentMediaTransferState(SentMediaMessageBinding binding, String messageId, TransferState state) {
        switch (state.getState()) {
            case TRANSFER_IN_PROGRESS -> {
                binding.flUploadOverlay.setVisibility(VISIBLE);
                binding.flRetryOverlay.setVisibility(GONE);
                binding.progressBarUpload.setProgress(state.getProgress());
                binding.ivCancelUpload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onCancelUpload(messageId);
                });
            }
            case TRANSFER_FAILED -> {
                binding.flUploadOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(VISIBLE);
                binding.ivRetryUpload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onRetryUpload(messageId);
                });
            }
            case TRANSFER_COMPLETED, TRANSFER_IDLE -> {
                binding.flUploadOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(GONE);
            }
        }
    }

    private void bindSentVideoTransferState(SentVideoMessageBinding binding, String messageId, TransferState state) {
        switch (state.getState()) {
            case TRANSFER_IN_PROGRESS -> {
                binding.flUploadOverlay.setVisibility(VISIBLE);
                binding.flRetryOverlay.setVisibility(GONE);
                binding.progressBarUpload.setProgress(state.getProgress());
                binding.ivCancelUpload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onCancelUpload(messageId);
                });
            }
            case TRANSFER_FAILED -> {
                binding.flUploadOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(VISIBLE);
                binding.ivRetryUpload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onRetryUpload(messageId);
                });
            }
            case TRANSFER_COMPLETED, TRANSFER_IDLE -> {
                binding.flUploadOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(GONE);
            }
        }
    }

    private void bindSentAudioTransferState(SentAudioMessageBinding binding, String messageId, TransferState state) {
        switch (state.getState()) {
            case TRANSFER_IN_PROGRESS -> {
                binding.progressBarUpload.setVisibility(VISIBLE);
                binding.ivCancelUpload.setVisibility(VISIBLE);
                binding.ivRetryUpload.setVisibility(GONE);
                binding.progressBarUpload.setProgress(state.getProgress());
                binding.ivCancelUpload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onCancelUpload(messageId);
                });
            }
            case TRANSFER_FAILED -> {
                binding.progressBarUpload.setVisibility(GONE);
                binding.ivCancelUpload.setVisibility(GONE);
                binding.ivRetryUpload.setVisibility(VISIBLE);
                binding.ivRetryUpload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onRetryUpload(messageId);
                });
            }
            case TRANSFER_COMPLETED, TRANSFER_IDLE -> {
                binding.progressBarUpload.setVisibility(GONE);
                binding.ivCancelUpload.setVisibility(GONE);
                binding.ivRetryUpload.setVisibility(GONE);
            }
        }
    }

    // -------------------- Receiver Transfer State Bind Helpers ---------------------

    private void bindReceivedMediaTransferState(ReceivedMediaMessageBinding binding, String messageId, TransferState state) {
        switch (state.getState()) {
            case TRANSFER_WAITING -> {
                binding.flDownloadOverlay.setVisibility(VISIBLE);
                binding.flDownloadProgressOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(GONE);
                binding.ivDownload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onDownload(messageId);
                });
            }
            case TRANSFER_IN_PROGRESS -> {
                binding.flDownloadOverlay.setVisibility(GONE);
                binding.flDownloadProgressOverlay.setVisibility(VISIBLE);
                binding.flRetryOverlay.setVisibility(GONE);
                binding.progressBarDownload.setProgress(state.getProgress());
            }
            case TRANSFER_FAILED -> {
                binding.flDownloadOverlay.setVisibility(GONE);
                binding.flDownloadProgressOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(VISIBLE);
                binding.ivRetryDownload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onRetryDownload(messageId);
                });
            }
            case TRANSFER_COMPLETED, TRANSFER_IDLE -> {
                binding.flDownloadOverlay.setVisibility(GONE);
                binding.flDownloadProgressOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(GONE);
            }
        }
    }

    private void bindReceivedVideoTransferState(ReceivedVideoMessageBinding binding, String messageId, TransferState state) {
        switch (state.getState()) {
            case TRANSFER_WAITING -> {
                binding.flDownloadOverlay.setVisibility(VISIBLE);
                binding.flDownloadProgressOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(GONE);
                binding.ivDownload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onDownload(messageId);
                });
            }
            case TRANSFER_IN_PROGRESS -> {
                binding.flDownloadOverlay.setVisibility(GONE);
                binding.flDownloadProgressOverlay.setVisibility(VISIBLE);
                binding.flRetryOverlay.setVisibility(GONE);
                binding.progressBarDownload.setProgress(state.getProgress());
            }
            case TRANSFER_FAILED -> {
                binding.flDownloadOverlay.setVisibility(GONE);
                binding.flDownloadProgressOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(VISIBLE);
                binding.ivRetryDownload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onRetryDownload(messageId);
                });
            }
            case TRANSFER_COMPLETED, TRANSFER_IDLE -> {
                binding.flDownloadOverlay.setVisibility(GONE);
                binding.flDownloadProgressOverlay.setVisibility(GONE);
                binding.flRetryOverlay.setVisibility(GONE);
            }
        }
    }

    private void bindReceivedAudioTransferState(ReceivedAudioMessageBinding binding, String messageId, TransferState state) {
        switch (state.getState()) {
            case TRANSFER_WAITING -> {
                binding.ivDownload.setVisibility(VISIBLE);
                binding.ivPlayPause.setVisibility(GONE);
                binding.progressBarDownload.setVisibility(GONE);
                binding.ivRetryDownload.setVisibility(GONE);
                binding.ivDownload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onDownload(messageId);
                });
            }
            case TRANSFER_IN_PROGRESS -> {
                binding.ivDownload.setVisibility(GONE);
                binding.ivPlayPause.setVisibility(GONE);
                binding.progressBarDownload.setVisibility(VISIBLE);
                binding.ivRetryDownload.setVisibility(GONE);
                binding.progressBarDownload.setProgress(state.getProgress());
            }
            case TRANSFER_FAILED -> {
                binding.ivDownload.setVisibility(GONE);
                binding.ivPlayPause.setVisibility(GONE);
                binding.progressBarDownload.setVisibility(GONE);
                binding.ivRetryDownload.setVisibility(VISIBLE);
                binding.ivRetryDownload.setOnClickListener(v -> {
                    if (mediaTransferClickListener != null) mediaTransferClickListener.onRetryDownload(messageId);
                });
            }
            case TRANSFER_COMPLETED, TRANSFER_IDLE -> {
                binding.ivDownload.setVisibility(GONE);
                binding.ivPlayPause.setVisibility(VISIBLE);
                binding.progressBarDownload.setVisibility(GONE);
                binding.ivRetryDownload.setVisibility(GONE);
            }
        }
    }

    // -------------------- TransferState Inner Class ---------------------

    /**
     * TransferState - Holds the current state and progress of a file upload/download.
     */
    public static class TransferState {

        private final int   state;
        private final int   progress;

        public TransferState(int state, int progress) {
            this.state      =   state;
            this.progress   =   progress;
        }

        public int getState() {
            return state;
        }

        public int getProgress() {
            return progress;
        }
    }

    // -------------------- Listener Interfaces ---------------------

    public interface OnMessageItemClickedListener {
        void onSentMessageLongClick(String messageId,int position);
        void onReceivedMessageLongClick(String messageId,int position);
        void onSentMessageClick(String messageId);
        void onSentRepliedMessageTextClick(String messageId);
        void onReceivedRepliedMessageTextClick(String messageId);
    }

    /**
     * Listener interface for media transfer actions triggered from the adapter UI.
     * Implement in MessagingActivity to wire to S3UploadHelper / S3DownloadHelper.
     */
    public interface OnMediaTransferClickListener {
        void onCancelUpload(String messageId);
        void onRetryUpload(String messageId);
        void onDownload(String messageId);
        void onRetryDownload(String messageId);
        /** User tapped an already-downloaded file — open it with the system viewer. */
        void onOpenFile(String messageId, String localFilePath);
        /** User tapped retry on a failed/stuck text message. */
        default void onRetryTextMessage(String messageId, String messageText) {}
    }
}
