// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.kit.chatkit.ui.view.message.viewholder;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.netease.nimlib.sdk.msg.constant.MsgDirectionEnum;
import com.netease.nimlib.sdk.msg.constant.MsgStatusEnum;
import com.netease.nimlib.sdk.msg.constant.SessionTypeEnum;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.kit.chatkit.model.IMMessageInfo;
import com.netease.yunxin.kit.chatkit.repo.ContactRepo;
import com.netease.yunxin.kit.chatkit.ui.ChatKitUIConstant;
import com.netease.yunxin.kit.chatkit.ui.ChatMessageType;
import com.netease.yunxin.kit.chatkit.ui.R;
import com.netease.yunxin.kit.chatkit.ui.common.MessageHelper;
import com.netease.yunxin.kit.chatkit.ui.custom.ChatConfigManager;
import com.netease.yunxin.kit.chatkit.ui.databinding.ChatBaseMessageViewHolderBinding;
import com.netease.yunxin.kit.chatkit.ui.model.ChatMessageBean;
import com.netease.yunxin.kit.chatkit.ui.view.input.ActionConstants;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.ChatMessageViewHolderUIOptions;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.CommonUIOption;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.CommonUIOption.MessageContentLayoutGravity;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.MessageStatusUIOption;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.SignalUIOption;
import com.netease.yunxin.kit.chatkit.ui.view.message.viewholder.options.UserInfoUIOption;
import com.netease.yunxin.kit.common.ui.utils.AvatarColor;
import com.netease.yunxin.kit.common.ui.utils.TimeFormatUtils;
import com.netease.yunxin.kit.common.utils.SizeUtils;
import com.netease.yunxin.kit.corekit.im.IMKitClient;
import com.netease.yunxin.kit.corekit.im.model.UserInfo;
import com.netease.yunxin.kit.corekit.im.provider.FetchCallback;
import java.util.List;

/** base message view holder for chat message item */
public abstract class ChatBaseMessageViewHolder extends CommonBaseMessageViewHolder {

  private static final String TAG = "ChatBaseMessageViewHolder";

  protected final ChatMessageViewHolderUIOptions defaultUIOptions =
      new ChatMessageViewHolderUIOptions.Builder().build();
  // viewHolder 类型
  protected int type;
  // 当前处理位置
  protected int position;
  // 当前渲染的消息体
  protected ChatMessageBean currentMessage;
  // 第一条回复消息
  protected IMMessageInfo replyMessage;
  // 基础 ui 的控件集合
  protected ChatBaseMessageViewHolderBinding baseViewBinding;
  // 基础 ui 控件集合的父容器
  protected ViewGroup parent;
  // ui 控制选项
  protected ChatMessageViewHolderUIOptions uiOptions;

  public ChatBaseMessageViewHolder(@NonNull ChatBaseMessageViewHolderBinding parent, int viewType) {
    super(parent.baseRoot);
    this.parent = parent.getRoot();
    this.type = viewType;
    baseViewBinding = parent;
  }

  public void onAttachedToWindow() {
    // 只针对收到的消息来发送已读回执，满足条件1.消息需要已读确认2.还没有发送已读回执
    if (messageReader == null
        || currentMessage == null
        || currentMessage.getMessageData() == null
        || currentMessage.getMessageData().getMessage().getDirect() != MsgDirectionEnum.In
        || !currentMessage.getMessageData().getMessage().needMsgAck()
        || currentMessage.getMessageData().getMessage().hasSendAck()) {
      return;
    }
    // 当消息被展示出时发送消息已读回执
    messageReader.messageRead(currentMessage.getMessageData());
  }

  //// 用于在某些操作下做部分页面更新
  public void bindData(ChatMessageBean message, int position, @NonNull List<?> payload) {
    uiOptions = getUIOptions(message);
    if (!payload.isEmpty()) {
      for (int i = 0; i < payload.size(); ++i) {
        String payloadItem = payload.get(i).toString();
        ALog.d(ChatKitUIConstant.LIB_TAG, TAG, "bindData,payload" + payloadItem);
        if (TextUtils.equals(payloadItem, ActionConstants.PAYLOAD_STATUS)) {
          // 消息状态更新
          currentMessage = message;
          setStatus(message);
          onMessageStatus(message);
        } else if (TextUtils.equals(payloadItem, ActionConstants.PAYLOAD_REVOKE)) {
          // 消息撤回
          onMessageRevoked(message);
          onMessageSignal(message);
        } else if (TextUtils.equals(payloadItem, ActionConstants.PAYLOAD_SIGNAL)) {
          // 消息标记
          onMessageSignal(message);
        } else if (TextUtils.equals(payloadItem, ActionConstants.PAYLOAD_PROGRESS)) {
          // 消息附件下载进度更新
          onProgressUpdate(message);
        } else if (TextUtils.equals(payloadItem, ActionConstants.PAYLOAD_USERINFO)) {
          // 消息体中用户信息更新
          setUserInfo(message);
          setStatus(message);
        } else if (TextUtils.equals(payloadItem, ActionConstants.PAYLOAD_REVOKE_STATUS)) {
          // 消息撤回时点击重新编辑超过可编辑时间时触发
          onMessageRevokeStatus(message);
          onMessageSignal(message);
        } else if (TextUtils.equals(payloadItem, ActionConstants.PAYLOAD_REPLY)) {
          // 消息回复
          setReplyInfo(message);
        }
        onCommonViewVisibleConfig(message);
        onMessageBackgroundConfig(message);
        onLayoutConfig(message);
      }
    }
    this.position = position;
  }
  //// 渲染每项的数据
  public void bindData(ChatMessageBean message, ChatMessageBean lastMessage) {
    uiOptions = getUIOptions(message);
    currentMessage = message;
    // 清空消息内容，初始化
    baseViewBinding.messageContainer.removeAllViews();
    baseViewBinding.messageBottomGroup.removeAllViews();
    baseViewBinding.messageTopGroup.removeAllViews();
    // 用于子类添加消息布局
    addViewToMessageContainer();
    // 若消息为通知/提示消息，则不进行后续内容设置，和 uikit 中默认逻辑有关
    if (type == ChatMessageType.NOTICE_MESSAGE_VIEW_TYPE
        || type == ChatMessageType.TIP_MESSAGE_VIEW_TYPE) {
      baseViewBinding.otherUserAvatar.setVisibility(View.GONE);
      baseViewBinding.myAvatar.setVisibility(View.GONE);
      baseViewBinding.messageStatus.setVisibility(View.GONE);
      baseViewBinding.tvTime.setVisibility(View.GONE);
      return;
    }
    // 设置消息点击回调集合
    setStatusCallback();
    // 渲染回复消息展示
    setReplyInfo(message);
    // 设置消息中的用户个人信息内容
    setUserInfo(message);
    // 设置消息状态展示
    setStatus(message);
    // 设置消息时间展示
    setTime(message, lastMessage);
    // 渲染标记消息布局
    onMessageSignal(message);
    // 渲染撤回消息布局
    onMessageRevoked(message);
    // 更新控件可见性
    onCommonViewVisibleConfig(message);
    // 设置消息背景
    onMessageBackgroundConfig(message);
    // 控制消息布局
    onLayoutConfig(message);
  }

  /**
   * 消息状态更新时触发此回调
   *
   * @param data 待展示消息体
   */
  protected void onMessageStatus(ChatMessageBean data) {}

  /**
   * 消息附件下载进度更新
   *
   * @param data 更新下载进度的消息体
   */
  protected void onProgressUpdate(ChatMessageBean data) {}
  //// 消息标记状态渲染，消息撤回时需要移除标记，添加标记也都会触发此方法，
  //// 是否存在标记内容需要根据 ChatMessageBean 中方法判断
  protected void onMessageSignal(ChatMessageBean data) {
    ALog.d(ChatKitUIConstant.LIB_TAG, TAG, "onMessageSignal" + data.getPinAccid());
    SignalUIOption signalUIOption = uiOptions.signalUIOption;
    if (signalUIOption.enable != null && !signalUIOption.enable) {
      // 标记功能未开启下则直接返回不做标记相关处理
      return;
    }
    if (!TextUtils.isEmpty(data.getPinAccid()) && !data.isRevoked()) {
      // 消息存在标记内容，且消息未被撤回展示标记相关ui
      baseViewBinding.llSignal.setVisibility(View.VISIBLE);
      String tid = null;
      if (data.getMessageData().getMessage().getSessionType() == SessionTypeEnum.Team) {
        tid = data.getMessageData().getMessage().getSessionId();
      }

      String nick = MessageHelper.getChatDisplayNameYou(tid, data.getPinAccid());
      if (data.getMessageData().getMessage().getSessionType() == SessionTypeEnum.P2P) {
        // 展示点对点聊天时标记内容及 ui
        if (signalUIOption.signalTipTextForP2p != null) {
          baseViewBinding.tvSignal.setText(signalUIOption.signalTipTextForP2p);
        } else {
          baseViewBinding.tvSignal.setText(
              String.format(
                  IMKitClient.getApplicationContext().getString(R.string.chat_message_signal_tip),
                  nick));
        }
      } else if (data.getMessageData().getMessage().getSessionType() == SessionTypeEnum.Team) {
        // 展示群聊天时标记内容及 ui
        if (signalUIOption.signalTipTextForGroup != null) {
          baseViewBinding.tvSignal.setText(signalUIOption.signalTipTextForGroup);
        } else {
          baseViewBinding.tvSignal.setText(
              String.format(
                  IMKitClient.getApplicationContext()
                      .getString(R.string.chat_message_signal_tip_for_team),
                  nick));
        }
      }
      // 判断是否存在自定义标记背景设置
      if (signalUIOption.signalBgRes != null) {
        // SignalUIOption 支持设置标记背景资源
        baseViewBinding.baseRoot.setBackgroundResource(signalUIOption.signalBgRes);
      } else if (properties.getSignalBgColor() != null) {
        // MessageProperties 支持设置标记背景颜色
        baseViewBinding.baseRoot.setBackgroundColor(properties.getSignalBgColor());
      } else {
        // 设置标记 ui 默认背景颜色
        baseViewBinding.baseRoot.setBackgroundResource(R.color.color_fffbea);
      }
    } else {
      // 无标记内容或消息已撤回不展示标记相关ui
      baseViewBinding.llSignal.setVisibility(View.GONE);
      // 无标记内容设置背景颜色未透明
      baseViewBinding.baseRoot.setBackgroundColor(
          parent.getContext().getResources().getColor(R.color.title_transfer));
    }
  }

  /**
   * 消息撤回时包含消息重新编辑设置，若点击此重新编辑按钮发现已经超过允许的标记时间触发此回调，并更新此消息 ui
   *
   * @param data 待展示消息体
   */
  protected void onMessageRevokeStatus(ChatMessageBean data) {}

  /**
   * 消息撤回时展示此消息在撤回状态下的 ui，是否为撤回消息需要自己在此方法中根据 ChatMessageBean 中的方法判断
   *
   * @param data 待展示消息体
   */
  protected void onMessageRevoked(ChatMessageBean data) {}

  /**
   * 设置消息的用户信息内容，包含头像以及昵称等
   *
   * @param message 待展示消息体
   */
  protected void setUserInfo(ChatMessageBean message) {
    // 默认下不展示消息发送方昵称
    baseViewBinding.otherUsername.setVisibility(View.GONE);
    // 处理消息发送方为他人的个人信息内容设置
    if (MessageHelper.isReceivedMessage(message)) {
      // 防止UserInfo数据不存在，若消息发送方不为当前用户好友可能发生此种情况需要额外请求数据并进行异步加载
      if (message.getMessageData().getFromUser() == null) {
        // TODO: 移除合并至repo
        ContactRepo.fetchUserInfo(
            message.getMessageData().getMessage().getFromAccount(),
            new FetchCallback<UserInfo>() {
              @Override
              public void onSuccess(@Nullable UserInfo param) {
                message.getMessageData().setFromUser(param);
                loadNickAndAvatarForOthers(message);
              }

              @Override
              public void onFailed(int code) {
                loadNickAndAvatarForOthers(message);
              }

              @Override
              public void onException(@Nullable Throwable exception) {
                loadNickAndAvatarForOthers(message);
              }
            });
      } else {
        // 用户信息存在直接设置对方的个人信息内容
        loadNickAndAvatarForOthers(message);
      }
    } else {
      // 处理消息发送方为自己时的个人信息内容设置
      loadNickAndAvatarForMy(message);
    }
  }

  /**
   * 加载当前用户头像和昵称
   *
   * @param message 待展示消息体
   */
  private void loadNickAndAvatarForMy(ChatMessageBean message) {
    // 用户信息ui自定义设置内容
    UserInfoUIOption userInfoUIOption = uiOptions.userInfoUIOption;
    // 头像圆角角度设置，可设置大的角度成为圆形
    if (userInfoUIOption.myAvatarRadius != null) {
      int cornerRadius = SizeUtils.dp2px(userInfoUIOption.myAvatarRadius);
      baseViewBinding.myAvatar.setCornerRadius(cornerRadius);
    } else if (properties.getAvatarCornerRadius() != null) {
      baseViewBinding.myAvatar.setCornerRadius(properties.getAvatarCornerRadius());
    }
    // 自定义设置当前用户头像尺寸大小
    if (userInfoUIOption.myAvatarSize != null) {
      int avatarSize = SizeUtils.dp2px(userInfoUIOption.myAvatarSize);
      ViewGroup.LayoutParams myAvatarLayoutParams = baseViewBinding.myAvatar.getLayoutParams();
      myAvatarLayoutParams.width = avatarSize;
      myAvatarLayoutParams.height = avatarSize;
      int marginForAvatar = 10;
      int size = SizeUtils.dp2px(userInfoUIOption.myAvatarSize + marginForAvatar);
      // 更新若头像消息后对头像依赖的间距
      updateGoneParam(size);
    }
    // 设置当前用户昵称
    String name = MessageHelper.getChatMessageUserName(message.getMessageData());
    if (!TextUtils.isEmpty(name)) {
      baseViewBinding.myName.setText(name);
    }
    // 设置当前用户头像
    String messageUser = message.getMessageData().getMessage().getFromAccount();
    UserInfo userInfo = MessageHelper.getChatMessageUserInfo(messageUser);
    if (userInfo == null) {
      userInfo = message.getMessageData().getFromUser();
    }
    if (userInfo != null) {
      String nickname = userInfo.getName();
      baseViewBinding.myAvatar.setData(
          userInfo.getAvatar(), nickname, AvatarColor.avatarColor(userInfo.getAccount()));
    }
    // 自定义设置是否展示当前用户头像
    if (userInfoUIOption.myAvatarVisible != null) {
      baseViewBinding.myAvatar.setVisibility(
          userInfoUIOption.myAvatarVisible ? View.VISIBLE : View.GONE);
    }
    // 自定义设置当前用户昵称文字颜色
    if (userInfoUIOption.myNickNameColor != null) {
      baseViewBinding.myName.setTextColor(userInfoUIOption.myNickNameColor);
    } else if (properties.getUserNickColor() != null) {
      baseViewBinding.myName.setTextColor(properties.getUserNickColor());
    }
    // 自定义设置当前用户昵称文字大小
    if (userInfoUIOption.myNicknameSize != null) {
      baseViewBinding.myName.setTextSize(userInfoUIOption.myNicknameSize);
    } else if (properties.getUserNickTextSize() != null) {
      baseViewBinding.myName.setTextSize(properties.getUserNickTextSize());
    }
    // 自定义设置当前用户昵称内容
    if (userInfoUIOption.myNickname != null) {
      baseViewBinding.myName.setText(userInfoUIOption.myNickname);
    }
    // 自定义设置当前用户昵称是否展示
    if (userInfoUIOption.myNicknameVisible != null) {
      baseViewBinding.myName.setVisibility(
          userInfoUIOption.myNicknameVisible ? View.VISIBLE : View.GONE);
    }
  }

  /**
   * 加载其他用户头像和昵称展示
   *
   * @param message 待展示消息体
   */
  private void loadNickAndAvatarForOthers(ChatMessageBean message) {
    // 获取对方用户昵称
    String name = MessageHelper.getChatMessageUserName(message.getMessageData());
    // 当前若时在群会话中，则展示对方用户昵称否则不展示
    if (message.getMessageData().getMessage().getSessionType() == SessionTypeEnum.Team) {
      baseViewBinding.otherUsername.setVisibility(View.VISIBLE);
      baseViewBinding.otherUsername.setText(name);
    } else {
      baseViewBinding.otherUsername.setVisibility(View.GONE);
    }
    // 获取对方用户头像
    String avatar =
        MessageHelper.getChatCacheAvatar(message.getMessageData().getMessage().getFromAccount());
    if (TextUtils.isEmpty(avatar)) {
      avatar =
          message.getMessageData().getFromUser() == null
              ? ""
              : message.getMessageData().getFromUser().getAvatar();
    }
    // 获取用户展示头像时文字内容
    String avatarName =
        message.getMessageData().getFromUser() == null
            ? message.getMessageData().getMessage().getFromAccount()
            : message.getMessageData().getFromUser().getUserInfoName();
    // 用户信息ui自定义设置内容
    UserInfoUIOption userInfoUIOption = uiOptions.userInfoUIOption;
    // 自定义设置对方用户头像圆角角度
    if (userInfoUIOption.otherUserAvatarRadius != null) {
      int cornerRadius = SizeUtils.dp2px(userInfoUIOption.otherUserAvatarRadius);
      baseViewBinding.otherUserAvatar.setCornerRadius(cornerRadius);
    } else if (properties.getAvatarCornerRadius() != null) {
      baseViewBinding.otherUserAvatar.setCornerRadius(properties.getAvatarCornerRadius());
    }
    // 自定义设置对方用户头像尺寸大小
    if (userInfoUIOption.otherUserAvatarSize != null) {
      int avatarSize = SizeUtils.dp2px(userInfoUIOption.otherUserAvatarSize);
      ViewGroup.LayoutParams myAvatarLayoutParams =
          baseViewBinding.otherUserAvatar.getLayoutParams();
      myAvatarLayoutParams.width = avatarSize;
      myAvatarLayoutParams.height = avatarSize;
      int marginForAvatar = 10;
      int size = SizeUtils.dp2px(userInfoUIOption.myAvatarSize + marginForAvatar);
      // 更新若头像消息后对头像依赖的间距
      updateGoneParam(size);
    }
    // 设置对方用户头像
    baseViewBinding.otherUserAvatar.setData(
        avatar,
        avatarName,
        AvatarColor.avatarColor(message.getMessageData().getMessage().getFromAccount()));
    // 自定义设置对方用户头像是否展示
    if (userInfoUIOption.otherUserAvatarVisible != null) {
      baseViewBinding.otherUserAvatar.setVisibility(
          userInfoUIOption.otherUserAvatarVisible ? View.VISIBLE : View.GONE);
    }
    // 自定义设置对方用户昵称文字颜色
    if (userInfoUIOption.otherUserNicknameColor != null) {
      baseViewBinding.otherUsername.setTextColor(userInfoUIOption.otherUserNicknameColor);
    } else if (properties.getUserNickColor() != null) {
      baseViewBinding.otherUsername.setTextColor(properties.getUserNickColor());
    }
    // 自定义设置对方用户昵称文字大小
    if (userInfoUIOption.otherUserNicknameSize != null) {
      baseViewBinding.otherUsername.setTextSize(userInfoUIOption.otherUserNicknameSize);
    } else if (properties.getUserNickTextSize() != null) {
      baseViewBinding.otherUsername.setTextSize(properties.getUserNickTextSize());
    }
    // 自定义设置对方用户昵称内容
    if (userInfoUIOption.otherUserNickname != null) {
      baseViewBinding.otherUsername.setText(userInfoUIOption.otherUserNickname);
    }
    // 自定义设置对方用户昵称是否展示
    if (userInfoUIOption.otherUserNicknameVisible != null) {
      baseViewBinding.otherUsername.setVisibility(
          userInfoUIOption.otherUserNicknameVisible ? View.VISIBLE : View.GONE);
    }
  }

  /**
   * 若当前消息为回复消息时，渲染回复状态的 ui，是否为回复消息需要用户在此方法中根据 ChatMessageBean 中方法判断
   *
   * @param messageBean 待展示消息体
   */
  protected void setReplyInfo(ChatMessageBean messageBean) {}

  /**
   * 设置消息展示的时间以及展示时间的间隔控制
   *
   * @param message 当前待展示的消息体
   * @param lastMessage 上一条已展示消息
   */
  protected void setTime(ChatMessageBean message, ChatMessageBean lastMessage) {
    // 通用自定义设置
    CommonUIOption commonUIOption = uiOptions.commonUIOption;
    // 获取待展示消息时间戳，若为 0 则设置为当前系统时间
    long createTime =
        message.getMessageData().getMessage().getTime() == 0
            ? System.currentTimeMillis()
            : message.getMessageData().getMessage().getTime();
    // 自定义设置时间格式，用户可以通过此设置修改时间戳的 format 格式
    if (commonUIOption.timeFormat != null) {
      baseViewBinding.tvTime.setText(String.format(commonUIOption.timeFormat, createTime));
    } else {
      baseViewBinding.tvTime.setText(
          TimeFormatUtils.formatMillisecond(itemView.getContext(), createTime));
    }
    // 自定义设置时间文本颜色
    if (commonUIOption.timeColor != null) {
      baseViewBinding.tvTime.setTextColor(commonUIOption.timeColor);
    } else if (properties.getTimeTextColor() != null) {
      baseViewBinding.tvTime.setTextColor(properties.getTimeTextColor());
    }
    // 自定义设置时间文本大小
    if (commonUIOption.timeSize != null) {
      baseViewBinding.tvTime.setTextSize(commonUIOption.timeSize);
    } else if (properties.getTimeTextSize() != null) {
      baseViewBinding.tvTime.setTextSize(properties.getTimeTextSize());
    }
    // 获取和上一条消息最小的时间间隔，在此间隔下消息之间不展示时间内容，否则展示
    // 用户可以通过 CommonUIOption#messageTimeIntervalMillisecond 完成修改，默认 5 分钟
    long intervalTime =
        commonUIOption.messageTimeIntervalMillisecond != null
            ? commonUIOption.messageTimeIntervalMillisecond
            : CommonUIOption.DEFAULT_MESSAGE_TIME_INTERVAL_MILLISECOND;
    if (lastMessage != null
        && createTime - lastMessage.getMessageData().getMessage().getTime() < intervalTime) {
      baseViewBinding.tvTime.setVisibility(View.GONE);
    } else {
      baseViewBinding.tvTime.setVisibility(View.VISIBLE);
    }
    // 自定义设置时间内容是否展示
    if (commonUIOption.timeVisible != null) {
      baseViewBinding.tvTime.setVisibility(commonUIOption.timeVisible ? View.VISIBLE : View.GONE);
    }
  }

  /**
   * 针对发送的消息，设置及更新消息已读/未读/下载进度/已读进度等/成功/失败，以及控制相关状态是否展示
   *
   * @param data 待展示消息体
   */
  protected void setStatus(ChatMessageBean data) {
    // 消息状态自定义设置
    MessageStatusUIOption messageStatusUIOption = uiOptions.messageStatusUIOption;
    // 自定义消息状态是否展示
    if (messageStatusUIOption.enableStatus != null && !messageStatusUIOption.enableStatus) {
      baseViewBinding.messageStatus.setVisibility(View.GONE);
      return;
    }
    // 撤回消息不展示状态
    if (data.isRevoked()) {
      baseViewBinding.messageStatus.setVisibility(View.GONE);
      return;
    }
    // 收到的消息不展示消息状态
    if (MessageHelper.isReceivedMessage(data)) {
      baseViewBinding.messageStatus.setVisibility(View.GONE);
      return;
    }
    // 展示相关状态配置
    baseViewBinding.messageStatus.setVisibility(View.VISIBLE);
    if (data.getMessageData().getMessage().getStatus() == MsgStatusEnum.sending) { // 消息发送过程状态展示
      baseViewBinding.ivStatus.setVisibility(View.GONE);
      baseViewBinding.readProcess.setVisibility(View.GONE);
      // 自定义是否展示消息发送中状态
      if (messageStatusUIOption.showSendingStatus != null) {
        baseViewBinding.messageSending.setVisibility(
            messageStatusUIOption.showSendingStatus ? View.VISIBLE : View.GONE);
      } else {
        baseViewBinding.messageSending.setVisibility(View.VISIBLE);
      }
    } else if (((data.getMessageData().getMessage().getStatus() == MsgStatusEnum.fail)
        || data.getMessageData().getMessage().isInBlackList())) { // 消息发送失败或者对方将自己拉黑时展示
      baseViewBinding.readProcess.setVisibility(View.GONE);
      baseViewBinding.messageSending.setVisibility(View.GONE);
      // 自定义设置消息失败图片资源
      if (messageStatusUIOption.failedFlagIconRes != null) {
        baseViewBinding.ivStatus.setImageResource(messageStatusUIOption.failedFlagIconRes);
      } else {
        baseViewBinding.ivStatus.setImageResource(R.drawable.ic_error);
      }
      // 自定义是否展示消息失败状态
      if (messageStatusUIOption.showFailedStatus != null) {
        baseViewBinding.ivStatus.setVisibility(
            messageStatusUIOption.showFailedStatus ? View.VISIBLE : View.GONE);
      } else {
        baseViewBinding.ivStatus.setVisibility(View.VISIBLE);
      }
    } else if (data.getMessageData().getMessage().getSessionType()
        == SessionTypeEnum.P2P) { // p2p 消息发送成功状态
      baseViewBinding.messageSending.setVisibility(View.GONE);
      baseViewBinding.readProcess.setVisibility(View.GONE);
      // 若消息不需要展示消息已读状态 或者 MessageProperties#getShowP2pMessageStatus 返回 false
      // 控制不展示点对点消息发送成功后的已读状态，则不进行点对点会话中消息已读状态展示，否则展示
      if (!properties.getShowP2pMessageStatus()
          || !data.getMessageData().getMessage().needMsgAck()
          || !ChatConfigManager.showReadStatus) {
        baseViewBinding.ivStatus.setVisibility(View.GONE);
      } else {
        baseViewBinding.ivStatus.setVisibility(View.VISIBLE);
        // 判断消息是否已读，根据已读状态设置对应状态图标
        if (data.getMessageData().getMessage().isRemoteRead()) {
          if (messageStatusUIOption.readFlagIconRes != null) {
            // 自定义设置消息已读状态图标资源
            baseViewBinding.ivStatus.setImageResource(messageStatusUIOption.readFlagIconRes);
          } else {
            baseViewBinding.ivStatus.setImageResource(R.drawable.ic_message_read);
          }
          data.setHaveRead(true);
        } else {
          if (messageStatusUIOption.unreadFlagIconRes != null) {
            // 自定义设置消息未读状态图标资源
            baseViewBinding.ivStatus.setImageResource(messageStatusUIOption.unreadFlagIconRes);
          } else {
            baseViewBinding.ivStatus.setImageResource(R.drawable.ic_message_unread);
          }
        }
      }
      // 自定义设置是否展示已读状态
      if (messageStatusUIOption.showReadStatus != null) {
        baseViewBinding.ivStatus.setVisibility(
            messageStatusUIOption.showReadStatus ? View.VISIBLE : View.GONE);
      }
    } else if (data.getMessageData().getMessage().getSessionType()
        == SessionTypeEnum.Team) { // 群聊消息发送成功状态
      baseViewBinding.messageSending.setVisibility(View.GONE);
      baseViewBinding.ivStatus.setVisibility(View.GONE);
      // 若群聊不需要展示已读，或者 MessageProperties#getShowTeamMessageStatus 返回 false
      // 控制不展示群聊的已读状态，则不进行点群会话中消息已读状态展示，否则展示
      if (!properties.getShowTeamMessageStatus()
          || !data.getMessageData().getMessage().needMsgAck()
          || !ChatConfigManager.showReadStatus) {
        baseViewBinding.readProcess.setVisibility(View.GONE);
        return;
      }
      // 获取群会话已读进度最大支持的人数，若群人数超过限制最大的人数则不展示已读进度状态，
      // 可通过 MessageStatusUIOption#maxReadingNum 设置，默认 100
      int maxNum =
          messageStatusUIOption.maxReadingNum != null
              ? messageStatusUIOption.maxReadingNum
              : MessageStatusUIOption.DEFAULT_MAX_READING_COUNT;
      if ((teamInfo != null && teamInfo.getMemberCount() >= maxNum)
          || !data.getMessageData().getMessage().needMsgAck()) {
        baseViewBinding.readProcess.setVisibility(View.GONE);
        return;
      }
      // 已读人数
      int ackCount = data.getMessageData().getMessage().getTeamMsgAckCount();
      // 未读人数
      int unAckCount = data.getMessageData().getMessage().getTeamMsgUnAckCount();
      // 人员总数
      float all = ackCount + unAckCount;
      // 若获取到 all 人数 <= 0 则不展示已读状态
      if (all > 0) {
        // 计算已读进度
        float process = ackCount / all;
        if (process < 1) {
          if (messageStatusUIOption.readProgressColor != null) {
            baseViewBinding.readProcess.setColor(messageStatusUIOption.readProgressColor);
          }
          baseViewBinding.readProcess.setProcess(process);
          baseViewBinding.readProcess.setVisibility(View.VISIBLE);
        } else {
          baseViewBinding.ivStatus.setVisibility(View.VISIBLE);
          baseViewBinding.ivStatus.setImageResource(R.drawable.ic_message_read);
          baseViewBinding.readProcess.setVisibility(View.GONE);
        }
      } else {
        baseViewBinding.readProcess.setVisibility(View.GONE);
      }

      // 自定义设置消息已读状态是否展示
      if (messageStatusUIOption.showReadStatus != null) {
        baseViewBinding.ivStatus.setVisibility(
            messageStatusUIOption.showReadStatus ? View.VISIBLE : View.GONE);
        baseViewBinding.readProcess.setVisibility(
            messageStatusUIOption.showReadStatus ? View.VISIBLE : View.GONE);
      }
    }
  }

  /**
   * 设置消息布局 layout 相关设置，由于收/发消息都在同一个布局文件中展示，需要通过此方法来修改 不同消息方向下的布局内容
   *
   * @param messageBean 待展示消息
   */
  protected void onLayoutConfig(ChatMessageBean messageBean) {
    ConstraintLayout.LayoutParams messageContainerLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.messageContainer.getLayoutParams();
    ConstraintLayout.LayoutParams messageTopLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.messageTopGroup.getLayoutParams();
    ConstraintLayout.LayoutParams messageBottomLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.messageBottomGroup.getLayoutParams();
    ConstraintLayout.LayoutParams signalLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.llSignal.getLayoutParams();
    if (MessageHelper.isReceivedMessage(messageBean)) {
      // 收到的消息设置消息体展示居左
      messageContainerLayoutParams.horizontalBias = MessageContentLayoutGravity.left;
      messageTopLayoutParams.horizontalBias = MessageContentLayoutGravity.left;
      messageBottomLayoutParams.horizontalBias = MessageContentLayoutGravity.left;
      baseViewBinding.llSignal.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    } else {
      // 发送的消息设置消息体展示居右
      messageContainerLayoutParams.horizontalBias = MessageContentLayoutGravity.right;
      messageTopLayoutParams.horizontalBias = MessageContentLayoutGravity.right;
      messageBottomLayoutParams.horizontalBias = MessageContentLayoutGravity.right;
      baseViewBinding.llSignal.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    }
    // 自定义消息布局，支持左中右三种，详细见 MessageContentLayoutGravity
    CommonUIOption commonUIOption = uiOptions.commonUIOption;
    if (commonUIOption.messageContentLayoutGravity != null) {
      messageContainerLayoutParams.horizontalBias = commonUIOption.messageContentLayoutGravity;
      messageTopLayoutParams.horizontalBias = commonUIOption.messageContentLayoutGravity;
      messageBottomLayoutParams.horizontalBias = commonUIOption.messageContentLayoutGravity;
    }
    baseViewBinding.llSignal.setLayoutParams(signalLayoutParams);
    baseViewBinding.messageContainer.setLayoutParams(messageContainerLayoutParams);
    baseViewBinding.messageBottomGroup.setLayoutParams(messageBottomLayoutParams);
    baseViewBinding.messageContainer.setLayoutParams(messageContainerLayoutParams);
    baseViewBinding.messageTopGroup.setLayoutParams(messageTopLayoutParams);
  }

  /**
   * 设置消息布局中某些控件的显示/隐藏
   *
   * @param messageBean 待展示消息
   */
  protected void onCommonViewVisibleConfig(ChatMessageBean messageBean) {
    if (MessageHelper.isReceivedMessage(messageBean)) {
      // 收到消息当前用户头像隐藏，对方用户头像显示
      baseViewBinding.myAvatar.setVisibility(View.GONE);
      baseViewBinding.otherUserAvatar.setVisibility(View.VISIBLE);
    } else {
      // 发送消息当前用户头像显示，对方用户头像隐藏
      baseViewBinding.myAvatar.setVisibility(View.VISIBLE);
      baseViewBinding.otherUserAvatar.setVisibility(View.GONE);
    }
    // 当消息为通知/提示消息时则双方头像、消息状态、时间等都不尽行展示
    if (type == ChatMessageType.NOTICE_MESSAGE_VIEW_TYPE
        || type == ChatMessageType.TIP_MESSAGE_VIEW_TYPE) {
      baseViewBinding.otherUserAvatar.setVisibility(View.GONE);
      baseViewBinding.myAvatar.setVisibility(View.GONE);
      baseViewBinding.messageStatus.setVisibility(View.GONE);
      baseViewBinding.tvTime.setVisibility(View.GONE);
    }
    // 撤回消息消息状态隐藏
    if (messageBean.isRevoked()) {
      baseViewBinding.messageStatus.setVisibility(View.GONE);
    }
  }

  /**
   * 用户设置消息体的背景，如根据不同消息方向展示不同的背景图
   *
   * @param messageBean 待展示消息
   */
  protected void onMessageBackgroundConfig(ChatMessageBean messageBean) {}

  /** 设置消息体布局控件点击回调监听 */
  protected void setStatusCallback() {
    if (itemClickListener == null) {
      return;
    }
    // 设置消息状态图标区域点击事件，仅当消息发送失败或在黑名单中时可点击
    baseViewBinding.ivStatus.setOnClickListener(
        v -> {
          if (currentMessage.getMessageData().getMessage().getStatus() == MsgStatusEnum.fail
              || currentMessage.getMessageData().getMessage().isInBlackList()) {
            itemClickListener.onSendFailBtnClick(v, position, currentMessage);
          }
        });
    // 设置消息对方用户头像区域点击事件
    baseViewBinding.otherUserAvatar.setOnClickListener(
        v -> itemClickListener.onUserIconClick(v, position, currentMessage));
    // 设置消息对方用户头像区域长按事件
    baseViewBinding.otherUserAvatar.setOnLongClickListener(
        v -> itemClickListener.onUserIconLongClick(v, position, currentMessage));
    // 设置消息当前用户头像区域点击事件
    baseViewBinding.myAvatar.setOnClickListener(
        v -> itemClickListener.onSelfIconClick(v, position, currentMessage));
    // 设置消息当前用户头像区域长按事件
    baseViewBinding.myAvatar.setOnLongClickListener(
        v -> itemClickListener.onSelfIconLongClick(v, position, currentMessage));
    // 设置消息内容区域长按事件
    baseViewBinding.messageContainer.setOnLongClickListener(
        v -> itemClickListener.onMessageLongClick(v, position, currentMessage));
    // 设置消息内容区域点击事件
    baseViewBinding.messageContainer.setOnClickListener(
        v -> itemClickListener.onMessageClick(v, position, currentMessage));
  }

  /**
   * 由于复用相同布局，若头像显示消失都会影响消息体的间距，设置头像消失下间距不变
   *
   * @param size 头像大小
   */
  protected void updateGoneParam(int size) {
    ConstraintLayout.LayoutParams messageContainerLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.messageContainer.getLayoutParams();
    messageContainerLayoutParams.goneRightMargin = size;
    messageContainerLayoutParams.goneLeftMargin = size;
    baseViewBinding.messageContainer.setLayoutParams(messageContainerLayoutParams);
    ConstraintLayout.LayoutParams messageBottomLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.messageBottomGroup.getLayoutParams();
    messageBottomLayoutParams.goneRightMargin = size;
    messageBottomLayoutParams.goneLeftMargin = size;
    baseViewBinding.messageBottomGroup.setLayoutParams(messageBottomLayoutParams);
    ConstraintLayout.LayoutParams messageTopLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.messageTopGroup.getLayoutParams();
    messageTopLayoutParams.goneRightMargin = size;
    messageTopLayoutParams.goneLeftMargin = size;
    baseViewBinding.messageTopGroup.setLayoutParams(messageTopLayoutParams);
    ConstraintLayout.LayoutParams signalLayoutParams =
        (ConstraintLayout.LayoutParams) baseViewBinding.llSignal.getLayoutParams();
    signalLayoutParams.goneLeftMargin = size;
    signalLayoutParams.goneRightMargin = size;
    baseViewBinding.llSignal.setLayoutParams(signalLayoutParams);
  }

  /** 可在此方法中实现将具体的消息体内容添加到消息容器中 */
  protected void addViewToMessageContainer() {}

  /** 获取消息内容展示容器 */
  protected ViewGroup getMessageContainer() {
    return baseViewBinding.messageContainer;
  }

  /**
   * 用于获取当前 ViewHolder 配置的自定义配置内容，若没有设置则使用{@link ChatBaseMessageViewHolder#defaultUIOptions}
   *
   * @param messageBean 消息的
   */
  private ChatMessageViewHolderUIOptions getUIOptions(ChatMessageBean messageBean) {
    ChatMessageViewHolderUIOptions uiOptions = provideUIOptions(messageBean);
    return uiOptions == null ? defaultUIOptions : uiOptions;
  }

  /**
   * 此方法可用于针对每条消息提供自定义的 ui 设置 注意，此方法是针对每条消息都会触发调用，若用户针对某类型消息的自定义配置相同可提供固定对象， 避免频繁调用导致性能降低，建议直接使用
   * {@link
   * ChatMessageViewHolderUIOptions#wrapExitsOptions(ChatMessageViewHolderUIOptions)}避免父类设置的自定义配置被丢失
   *
   * @param messageBean 根据此消息自定义 ui 设置
   * @return 自定义 ui 设置
   */
  protected ChatMessageViewHolderUIOptions provideUIOptions(ChatMessageBean messageBean) {
    return null;
  }
}