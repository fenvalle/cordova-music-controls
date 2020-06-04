package com.homerours.musiccontrols;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import com.homerours.musiccontrols.MusicControlsNotificationKiller.NotificationBinder;

public class MusicControls extends CordovaPlugin {
	private MusicControlsBroadcastReceiver mMessageReceiver = new MusicControlsBroadcastReceiver(this);
	private MusicControlsNotification notification;
	private MediaSessionCompat mediaSessionCompat;
	private PendingIntent mediaButtonPendingIntent;
	private MusicControlsNotificationKiller service;
	private MediaSessionCallback mMediaSessionCallback = new MediaSessionCallback();
	private BroadcastReceiver receiver = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { cordova.getActivity().finish(); }};
	private final int notificationID=7824;
	private boolean listening=false;
	private boolean hasInstance=false;
	private Activity cordovaActivity;
	private Bitmap bitmapCover;
	private String bitmapUrl;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		this.cordovaActivity = this.cordova.getActivity();
		cordovaActivity.registerReceiver(receiver, new IntentFilter(("MusicControls")));
		this.registerMediaSession();
		this.registerService();
		this.registerBroadcaster();
	}

	private void registerService() {
		// attach to service, and create a Killer Instance, and Notification Instance
		ServiceConnection mConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName className, IBinder service) {
				((MusicControlsNotificationKiller.NotificationBinder) service).service.startService(new Intent(cordovaActivity, MusicControlsNotificationKiller.class));
				MusicControls.this.service = ((NotificationBinder) service).getInstance();
				MusicControls.this.notification = new MusicControlsNotification(cordovaActivity, MusicControls.this.service, MusicControls.this.notificationID, MusicControls.this.mediaSessionCompat);
			}
			@Override public void onServiceDisconnected(ComponentName className) { }
		};
		cordovaActivity.bindService(new Intent(cordovaActivity,MusicControlsNotificationKiller.class).putExtra("notificationID",this.notificationID).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), mConnection, Context.BIND_AUTO_CREATE);
	}
	private void registerMediaSession() {
		final Context context = this.cordova.getActivity().getApplicationContext();
		this.mediaButtonPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent("music-controls-media-button"), PendingIntent.FLAG_UPDATE_CURRENT);
		this.mediaSessionCompat = new MediaSessionCompat(context, "cordova-music-controls-media-session", null, PendingIntent.getBroadcast(context, 0, new Intent("music-controls-media-button"), PendingIntent.FLAG_UPDATE_CURRENT));
		this.mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
		this.mediaSessionCompat.setActive(true);
		this.mediaSessionCompat.setCallback(this.mMediaSessionCallback);
		setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
	}
	public void unregisterMediaButtonEvent() {
		try { this.mediaSessionCompat.setMediaButtonReceiver(null); } catch (Exception e) { e.printStackTrace(); }
	}
	public void registerMediaButtonEvent(){
		try { this.mediaSessionCompat.setMediaButtonReceiver(mediaButtonPendingIntent); } catch (Exception e) { e.printStackTrace(); }
	}
	private void registerBroadcaster(){
		if (this.listening == true) return;
		this.listening = true;
		final Context context = this.cordova.getActivity().getApplicationContext();
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-previous"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-pause"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-play"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-next"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-media-button"));
		context.registerReceiver(mMessageReceiver, new IntentFilter("music-controls-destroy"));
		context.registerReceiver(mMessageReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
	}
	//received destroy
	public void destroyPlayerNotification(){
		if (this.listening == false) return;

		this.hasInstance = false;
		this.listening = false;
		this.unregisterMediaButtonEvent();
		this.notification.destroy();
	}

	@Override
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
		switch (action){
			case "create":{
				this.hasInstance = true;
				this.registerBroadcaster();
				final MusicControlsInfos infos = new MusicControlsInfos(args);
				final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

				this.cordova.getThreadPool().execute(() -> {
					try {
						this.bitmapCover = getBitmapCover(infos.cover);
						this.notification.createNotification(infos, this.bitmapCover);

						setMediaPlaybackState(infos.isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
						metadataBuilder
							.putString(MediaMetadataCompat.METADATA_KEY_TITLE, infos.track)
							.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, infos.artist)
							.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, infos.album);
						if(this.bitmapCover != null) metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, this.bitmapCover);
						if(this.bitmapCover != null) metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, this.bitmapCover);
						mediaSessionCompat.setMetadata(metadataBuilder.build());

						callbackContext.success("success");
					} catch (Exception ex) { callbackContext.error(ex.getMessage()); }
				});
				return true;
			}
			case "updateIsPlaying": {
				if (this.hasInstance == false) return true;
				this.registerBroadcaster();
				final boolean isPlaying = args.getJSONObject(0).getBoolean("isPlaying");
				this.cordova.getThreadPool().execute(() -> {
					notification.updateIsPlaying(isPlaying);
					setMediaPlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
					callbackContext.success("success");
				});
				return true;
			}
			case "updateDismissable": {
				if (this.hasInstance == false) return true;
				final boolean dismissable = args.getJSONObject(0).getBoolean("dismissable");

				this.cordova.getThreadPool().execute(() -> {
					notification.updateDismissable(dismissable);
					callbackContext.success("success");
				});
				return true;
			}
			case "destroy":{
				if (this.hasInstance == false) return true;
				destroyPlayerNotification();
				callbackContext.success("success");
				return true;
			}
			case "watch":{
				if (this.hasInstance == false) return true;
				this.cordova.getThreadPool().execute(() -> {
					registerBroadcaster();
					registerMediaButtonEvent();
					mMediaSessionCallback.setCallback(callbackContext);
					mMessageReceiver.setReceiverCallback(callbackContext);
				});
				return true;
			}
		}
		return true;
	}
	public void onPause(boolean multitasking)
	{
		if (this.listening == false) return;
		if (this.hasInstance == false) return;

		try {
			this.notification.setInBackground(true);
			disableWebViewOptimizations();
		} finally { }
	}
	public void onResume (boolean multitasking) {
		if (this.listening == false) return;
		if (this.hasInstance == false) return;

		try {
			this.notification.setInBackground(false);
		} finally { }
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onReset() {
		onDestroy();
		super.onReset();
	}

	private void disableWebViewOptimizations() {
		Thread thread = new Thread(){
			public void run() {
				try {
					Thread.sleep(1000);
					cordova.getActivity().runOnUiThread(() -> {
						View view = webView.getView();
						try { Class.forName("org.crosswalk.engine.XWalkCordovaView").getMethod("onShow").invoke(view); }
						catch (Exception e){ view.dispatchWindowVisibilityChanged(View.VISIBLE); }
					});
				} catch (InterruptedException e) { }
			}
		};

		thread.start();
	}

	private void setMediaPlaybackState(int state) {
		PlaybackStateCompat.Builder pBuild = new PlaybackStateCompat.Builder();
		if( state == PlaybackStateCompat.STATE_PLAYING ) {
			pBuild.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			pBuild.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
		} else {
			pBuild.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			pBuild.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0);
		}
		this.mediaSessionCompat.setPlaybackState(pBuild.build());
	}

	private Bitmap getBitmapCover(String coverURL){
		if (coverURL == this.bitmapUrl) return this.bitmapCover;
		try{
			return coverURL.matches("^(https?|ftp)://.*$")
					? getBitmapFromURL(coverURL)
					: getBitmapFromLocal(coverURL);
		} catch (Exception ex) { return null; }
	}

	private Bitmap getBitmapFromLocal(String localURL){
		try {
			Uri uri = Uri.parse(localURL);
			BufferedInputStream buf = new BufferedInputStream(new FileInputStream(new File(uri.getPath())));
			Bitmap myBitmap = BitmapFactory.decodeStream(buf);
			buf.close();
			this.bitmapUrl = localURL;
			return myBitmap;
		} catch (Exception ex) {
			try {
				BufferedInputStream buf = new BufferedInputStream(cordovaActivity.getApplicationContext().getAssets().open("www/" + localURL));
				Bitmap myBitmap = BitmapFactory.decodeStream(buf);
				buf.close();
				this.bitmapUrl = localURL;
				return myBitmap;
			} catch (Exception ex2) {
				ex.printStackTrace();
				ex2.printStackTrace();
				return null;
			}
		}
	}

	// get Remote image
	private Bitmap getBitmapFromURL(String strURL) {
		try {
			URL url = new URL(strURL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			this.bitmapUrl = strURL;
			return myBitmap;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
}
