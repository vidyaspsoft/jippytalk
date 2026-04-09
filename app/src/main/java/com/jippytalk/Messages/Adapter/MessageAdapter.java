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
import com.jippytalk.databinding.ReceivedMessageLocationBinding;
import com.jippytalk.databinding.ReceiverMessageBinding;
import com.jippytalk.databinding.SenderMessageBinding;
import com.jippytalk.databinding.SentLocationBinding;
import com.jippytalk.databinding.SystemMessagesBinding;

import java.util.List;


public class MessageAdapter extends ListAdapter<MessageModal, RecyclerView.ViewHolder> {

    private final Activity                      context;
    private final int                           messageTheme;
    private Animation                           loading_anim;
    private final SharedPreferences             sharedPreferences;
    private final OnMessageItemClickedListener  onItemClickListener;
    private int                                 selectedItemPosition =  -1;
    private final MessageUtils                  messageUtils;

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
            case MessagesManager.SYSTEM_MESSAGE_TYPE        ->  ((SystemMessageViewHolder) holder).bind(messageModal);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("STATUS")) {
            MessageModal messageModal = getItem(position);
            if (holder instanceof SentMessageViewHolder sentHolder) {
                setStatusIcon(messageModal.getMessageStatus(), sentHolder.senderMessageBinding.ivSeenImage);
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
            if (type == MessagesManager.LOCATION_MESSAGE) return MessagesManager.SENT_LOCATION;
            return MessagesManager.SENT_MESSAGE; // includes TEXT_MESSAGE with isReply=0 or LINK_MESSAGE
        }

        // Received messages
        if (direction == MessagesManager.RECEIVED_MESSAGE) {
            if (type == MessagesManager.LOCATION_MESSAGE) return MessagesManager.RECEIVED_LOCATION;
            return MessagesManager.RECEIVED_MESSAGE; // includes TEXT_MESSAGE with isReply=0 or LINK_MESSAGE
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

                if (repliedToText == null || repliedToText.isEmpty()) {
                    senderMessageBinding.selectedMsgTxt.setText(R.string.message_deleted);
                } else {
                    senderMessageBinding.selectedMsgTxt.setText(repliedToText);
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
                receiverMessageBinding.tvSelectedMsgTxt.setText(repliedToText);

                if (repliedToMessageDirection == MessagesManager.MESSAGE_INCOMING) {
                    receiverMessageBinding.tvMsgSenderName.setText(repliedToMessageSender);
                } else {
                    receiverMessageBinding.tvMsgSenderName.setText(R.string.you);
                }
            } else {
                receiverMessageBinding.cvReceivedMsgToMsgReply.setVisibility(GONE);
                receiverMessageBinding.tvMsgSenderName.setVisibility(GONE);
                receiverMessageBinding.tvSelectedMsgTxt.setVisibility(GONE);
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

    private void setStatusIcon(int messageStatus, ImageView imageView) {
        switch (messageStatus) {
            case MessagesManager.MESSAGE_DELIVERED, MessagesManager.MESSAGE_DELIVERED_LOCALLY -> {
               // imageView.clearAnimation();
                imageView.setImageResource(R.drawable.message_not_seen);
            }
            case MessagesManager.MESSAGE_SEEN, MessagesManager.MESSAGE_SEEN_LOCALLY -> {
                imageView.clearAnimation();
                imageView.setImageResource(R.drawable.message_seen);
            }
            case MessagesManager.MESSAGE_SYNCED_WITH_SERVER, MessagesManager.UNKNOWN_NUMBER_MESSAGE -> {
                imageView.clearAnimation();
                imageView.setImageResource(R.drawable.message_sent);
            }
            case MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER -> {
                imageView.setImageResource(R.drawable.ic_baseline_access_time_24);
                // imageView.startAnimation(loading_anim);
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

    public int findMessagePositionById(String messageId) {
        List<MessageModal> messages = getCurrentList(); // or whatever your list is
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getMessageId().equals(messageId)) {
                return i;
            }
        }
        return -1; // not found
    }

    public interface OnMessageItemClickedListener {
        void onSentMessageLongClick(String messageId,int position);
        void onReceivedMessageLongClick(String messageId,int position);
        void onSentMessageClick(String messageId);
        void onSentRepliedMessageTextClick(String messageId);
        void onReceivedRepliedMessageTextClick(String messageId);
    }
}
