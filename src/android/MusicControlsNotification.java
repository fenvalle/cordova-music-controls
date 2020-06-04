package com.homerours.musiccontrols;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.session.MediaSessionCompat;

public class MusicControlsNotification {
	private Activity cordovaActivity;
	private NotificationManager notificationManager;
	private int notificationID;
	private boolean inBackground = false;
	private MusicControlsInfos infos;
	private Bitmap bitmapCover;
	private String CHANNEL_ID ="cordova-music-channel-id";
	private MusicControlsNotificationKiller service;
	private MediaSessionCompat mediaSessionCompat;

	public MusicControlsNotification(Activity cordovaActivity, MusicControlsNotificationKiller service, int id, MediaSessionCompat mediaSessionCompat){
		this.mediaSessionCompat = mediaSessionCompat;
		this.service = service;
		this.notificationID = id;
		this.cordovaActivity = cordovaActivity;
		this.notificationManager = (NotificationManager) cordovaActivity.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public void createNotification(MusicControlsInfos newInfos, Bitmap bitmap) {
		this.bitmapCover = bitmap;
		this.infos = newInfos;
		displayNotification();
	}
	public void updateIsPlaying(boolean isPlaying) {
		if(this.infos == null) return;
		this.infos.isPlaying=isPlaying;
		displayNotification();
	}
	public void updateDismissable(boolean value){
		this.infos.dismissable=value;
		displayNotification();
	}

	public void setInBackground(boolean newStatus) {
		this.inBackground = newStatus;
		displayNotification();
	}

	private void displayNotification() {
		Notification notification = createNotification();
		this.notificationManager.notify(this.notificationID, notification);

		if (this.inBackground && this.infos.isPlaying == true) {
			this.service.keepAwake();
			this.service.startForeground(notification);
		} else if (this.inBackground && this.infos.isPlaying == false) {
			this.service.sleepWell();
			this.service.stopForegroundNotification(false);
		}
	}

	private Notification createNotification() {
		if (Build.VERSION.SDK_INT >= 26) {
			NotificationChannel channel = new NotificationChannel(this.CHANNEL_ID, "Audio Controls", NotificationManager.IMPORTANCE_LOW);
			channel.setDescription("Control Playing Audio");
			channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
			this.notificationManager.createNotificationChannel(channel);
		};

		NotificationCompat.Builder builder = new NotificationCompat.Builder(cordovaActivity.getApplicationContext(), CHANNEL_ID);

		int smallIcon = infos.notificationIcon.isEmpty() ? 0 : this.getResourceId(infos.notificationIcon, 0);
		if (smallIcon == 0) smallIcon = infos.isPlaying ? this.getResourceId(infos.playIcon, android.R.drawable.ic_media_play) : this.getResourceId(infos.pauseIcon, android.R.drawable.ic_media_pause);

		if (infos.hasPrev) builder.addAction(this.getResourceId(infos.prevIcon, android.R.drawable.ic_media_previous), "", PendingIntent.getBroadcast(cordovaActivity, 1, new Intent("music-controls-previous"), 0));
		if (!infos.isPlaying) builder.addAction(this.getResourceId(infos.playIcon, android.R.drawable.ic_media_play), "", PendingIntent.getBroadcast(cordovaActivity, 1, new Intent("music-controls-play"), 0));
		if (infos.isPlaying)  builder.addAction(this.getResourceId(infos.pauseIcon, android.R.drawable.ic_media_pause), "", PendingIntent.getBroadcast(cordovaActivity, 1, new Intent("music-controls-pause"), 0));
		if (infos.hasNext) builder.addAction(this.getResourceId(infos.nextIcon, android.R.drawable.ic_media_next), "", PendingIntent.getBroadcast(cordovaActivity, 1, new Intent("music-controls-next"), 0));
		if (!infos.isPlaying) builder.addAction(this.getResourceId("media_stop", android.R.drawable.ic_media_ff), "", PendingIntent.getBroadcast(cordovaActivity, 1, new Intent("music-controls-destroy"), 0));

		int buttons = infos.hasPrev ? infos.hasNext ? 3: 2: 1;
		int[] args = new int[buttons];
		for (int i = 0; i < buttons; ++i) args[i] = i;

		PendingIntent pendingIntent = PendingIntent.getActivity(cordovaActivity, 0, new Intent(cordovaActivity, cordovaActivity.getClass()).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
		builder
				.setSmallIcon(smallIcon)
				.setSound(null)
				.setDeleteIntent(PendingIntent.getBroadcast(cordovaActivity, 1, new Intent("music-controls-destroy"), 0))
				.setContentIntent(pendingIntent)
				.setOngoing(infos.dismissable && infos.isPlaying)
				.setContentTitle(infos.track)
				.setContentText(infos.artist)
				.setSubText(infos.ticker)
				.setWhen(0)
				.setColor(cordovaActivity.getResources().getColor(android.R.color.darker_gray))
				.setColor(Color.BLACK)
				.setShowWhen(false)
				.setStyle(new MediaStyle().setShowActionsInCompactView(args).setMediaSession(mediaSessionCompat.getSessionToken()))
				.setPriority(Notification.PRIORITY_LOW)
				.setVisibility(Notification.VISIBILITY_PUBLIC)
				.setCategory(Notification.CATEGORY_PROGRESS)
				.setTicker(infos.ticker.isEmpty() ? "" : infos.ticker);

		if (Build.VERSION.SDK_INT >= 26) builder.setColorized(true);
		if (this.bitmapCover != null && !infos.cover.isEmpty()) builder.setLargeIcon(this.bitmapCover);
		return builder.build();
	}


	private int getResourceId(String name, int fallback){
		try{
			if(name.isEmpty()) return fallback;
			int resId = cordovaActivity.getResources().getIdentifier(name, "drawable", this.cordovaActivity.getPackageName());
			return resId == 0 ? fallback : resId;
		}
		catch(Exception ex){ return fallback; }
	}

	public void destroy(){
		try {
			if(Build.VERSION.SDK_INT >= 26) notificationManager.deleteNotificationChannel(this.CHANNEL_ID);
			this.notificationManager.cancel(this.notificationID);
		} finally {}
		try {
			this.service.stopForegroundNotification(true);
		} finally {
			this.service.sleepWell();
		}
	}
}
