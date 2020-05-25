package com.homerours.musiccontrols;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.Build;
import android.os.PowerManager;
import android.content.BroadcastReceiver;
import android.media.AudioManager;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static android.content.Context.BIND_AUTO_CREATE;

public class MusicControls extends CordovaPlugin {
	private MusicControlsBroadcastReceiver mMessageReceiver;
	private MusicControlsNotification notification;
	private MediaSessionCompat mediaSessionCompat;
	private final int notificationID=7824;
	private AudioManager mAudioManager;
	private PendingIntent mediaButtonPendingIntent;
	private boolean mediaButtonAccess=true;
	private Activity cordovaActivity;
	public boolean inBackground = false;
	private ServiceConnection mConnection;
	private enum BackgroundEvent { ACTIVATE, DEACTIVATE, FAILURE }

	private Intent startServiceIntent;
	private MediaSessionCallback mMediaSessionCallback = new MediaSessionCallback();

	private void registerBroadcaster(MusicControlsBroadcastReceiver mMessageReceiver){
		final Context context = this.cordova.getActivity().getApplicationContext();
		context.registerReceiver((BroadcastReceiver)mMessageReceiver, new IntentFilter("music-controls-previous"));
		context.registerReceiver((BroadcastReceiver)mMessageReceiver, new IntentFilter("music-controls-pause"));
		context.registerReceiver((BroadcastReceiver)mMessageReceiver, new IntentFilter("music-controls-play"));
		context.registerReceiver((BroadcastReceiver)mMessageReceiver, new IntentFilter("music-controls-next"));
		context.registerReceiver((BroadcastReceiver)mMessageReceiver, new IntentFilter("music-controls-media-button"));
		context.registerReceiver((BroadcastReceiver)mMessageReceiver, new IntentFilter("music-controls-destroy"));

	// Listen for headset plug/unplug
		context.registerReceiver((BroadcastReceiver)mMessageReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) { cordova.getActivity().finish(); }
	};

	// Register pendingIntent for broadcast
	public void registerMediaButtonEvent(){
		this.mediaSessionCompat.setMediaButtonReceiver(this.mediaButtonPendingIntent);
	}

	public void unregisterMediaButtonEvent(){
		this.mediaSessionCompat.setMediaButtonReceiver(null);
	}

	public void destroyPlayerNotification(){
		this.notification.destroy();
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		final Activity activity = this.cordova.getActivity();
		final Context context=activity.getApplicationContext();
		cordova.getActivity().registerReceiver(receiver, new IntentFilter(("MusicControls")));
		this.cordovaActivity = activity;

		this.notification = new MusicControlsNotification(activity,this.notificationID);
		this.mMessageReceiver = new MusicControlsBroadcastReceiver(this);
		this.registerBroadcaster(mMessageReceiver);

		
		this.mediaSessionCompat = new MediaSessionCompat(context, "cordova-music-controls-media-session", null, this.mediaButtonPendingIntent);
		this.mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);


		setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
		this.mediaSessionCompat.setActive(true);

		this.mediaSessionCompat.setCallback(this.mMediaSessionCallback);
		
		// Register media (headset) button event receiver
		try {
			this.mAudioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
			Intent headsetIntent = new Intent("music-controls-media-button");
			this.mediaButtonPendingIntent = PendingIntent.getBroadcast(context, 0, headsetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			this.registerMediaButtonEvent();
		} catch (Exception e) {
			this.mediaButtonAccess=false;
			e.printStackTrace();
		}

		// Notification Killer
		ServiceConnection mConnection = new ServiceConnection() {
			public void onServiceConnected(ComponentName className, IBinder binder) {
				((KillBinder) binder).service.startService(new Intent(activity, MusicControlsNotificationKiller.class));
			}
			public void onServiceDisconnected(ComponentName className) { }
		};
		this.startServiceIntent = new Intent(activity,MusicControlsNotificationKiller.class);
		this.startServiceIntent.putExtra("notificationID",this.notificationID);
		this.startServiceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, this.startServiceIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		activity.bindService(this.startServiceIntent, mConnection, Context.BIND_AUTO_CREATE);

		this.mConnection = mConnection;
		//startForegroundService
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(this.startServiceIntent);
		} else {
			context.startService(this.startServiceIntent);
		}
	}

	@Override
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
		final Context context=this.cordova.getActivity().getApplicationContext();

		if (action.equals("create")) {
			final MusicControlsInfos infos = new MusicControlsInfos(args);
			 final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

			this.cordova.getThreadPool().execute(() -> {
				try {
				notification.updateNotification(infos);
					
					// track title
					metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, infos.track);
					// artists
					metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, infos.artist);
					//album
					metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, infos.album);

					Bitmap art = getBitmapCover(infos.cover);
					if(art != null){
						metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
						metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);

					}
					mediaSessionCompat.setMetadata(metadataBuilder.build());

					if(infos.isPlaying)
						setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
					else
						setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

					callbackContext.success("success");
				} catch (Exception ex) {
					callbackContext.error(ex.getMessage());
				}
			});
		}

		else if (action.equals("updateIsPlaying")){
			final JSONObject params = args.getJSONObject(0);
			final boolean isPlaying = params.getBoolean("isPlaying");
			
			this.cordova.getThreadPool().execute(() -> {
				notification.updateIsPlaying(isPlaying);
				if (isPlaying) {
					setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
				}
				else {
					setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
				}

				callbackContext.success("success");
			});
		}

		else if (action.equals("updateDismissable")){
			final JSONObject params = args.getJSONObject(0);
			final boolean dismissable = params.getBoolean("dismissable");
			
			this.cordova.getThreadPool().execute(() -> {
				notification.updateDismissable(dismissable);
				callbackContext.success("success");
			});
		}

		else if (action.equals("destroy")){
			this.notification.destroy();
			this.mMessageReceiver.stopListening();
			callbackContext.success("success");
		}

		else if (action.equals("watch")) {
			this.cordova.getThreadPool().execute(() -> {
				registerMediaButtonEvent();
				mMediaSessionCallback.setCallback(callbackContext);
				mMessageReceiver.setCallback(callbackContext);
			});
		}
		return true;
	}
	public void onPause(boolean multitasking)
	{
		LOG.d("LOG", "Paused the activity.");
		try {
			inBackground = true;
			startService();
			disableWebViewOptimizations();
		} finally {
		}
	}
	public void onStop () {
		LOG.d("LOG", "Stopped the activity.");
	}
	public void onResume (boolean multitasking)
	{
		LOG.d("LOG", "Resumed the activity.");
		inBackground = false;
		stopService();
	}

	@Override
	public void onDestroy() {
		stopService();
		this.notification.destroy();
		this.mMessageReceiver.stopListening();
		this.unregisterMediaButtonEvent();
		super.onDestroy();

		final Context context = this.cordova.getActivity().getApplicationContext();
		context.stopService(this.startServiceIntent);
	}

	@Override
	public void onReset() {
		onDestroy();
		super.onReset();
	}

	private void startService()
	{
		LOG.d("LOG", "Start Service");
		Intent intent = new Intent(cordovaActivity, MusicControlsNotificationKiller.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			//cordovaActivity.bindService(intent, mConnection, BIND_AUTO_CREATE);
			fireEvent(BackgroundEvent.ACTIVATE, null);
			//cordovaActivity.startService(intent);
			//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) cordovaActivity.startForegroundService(intent);
			//else cordovaActivity.startService(intent);
		} catch (Exception e) {
			fireEvent(BackgroundEvent.FAILURE, String.format("'%s'", e.getMessage()));
		}
	}

	private void stopService()
	{
		LOG.d("LOG", "stop Service");
		Activity context = cordova.getActivity();
		Intent intent    = new Intent(context, MusicControlsNotificationKiller.class);

		fireEvent(BackgroundEvent.DEACTIVATE, null);
		context.stopService(intent);
	}

	private MusicControlsNotificationKiller service;

	private void fireEvent (BackgroundEvent event, String params)
	{
		String eventName = event.name().toLowerCase();
		Boolean active   = event == BackgroundEvent.ACTIVATE;

		String str = String.format("%s._setActive(%b)", "MusicControls", active);
		str = String.format("%s;%s.on('%s', %s)", str, "MusicControls", eventName, params);
		str = String.format("%s;%s.fireEvent('%s',%s);", str, "MusicControls", eventName, params);
		final String js = str;

		cordova.getActivity().runOnUiThread(() -> webView.loadUrl("javascript:" + js));
	}

	private void disableWebViewOptimizations() {
		Thread thread = new Thread(){
			public void run() {
				try {
					Thread.sleep(1000);
					cordova.getActivity().runOnUiThread(() -> {
						View view = webView.getView();
						LOG.d("LOG", "sleep action");

						try {
							Class.forName("org.crosswalk.engine.XWalkCordovaView")
									.getMethod("onShow")
									.invoke(view);

							LOG.d("LOG", "disabling webviewOptimizations");
						} catch (Exception e){
							view.dispatchWindowVisibilityChanged(View.VISIBLE);
						}
					});
				} catch (InterruptedException e) { }
			}
		};

		thread.start();
	}



	private void setMediaPlaybackState(int state) {
		PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
		if( state == PlaybackStateCompat.STATE_PLAYING ) {
			playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
				PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
				PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			playbackstateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
		} else {
			playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
				PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
				PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
			playbackstateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0);
		}
		this.mediaSessionCompat.setPlaybackState(playbackstateBuilder.build());
	}
	
	// Get image from url
	private Bitmap getBitmapCover(String coverURL){
		try{
			if(coverURL.matches("^(https?|ftp)://.*$"))
				// Remote image
				return getBitmapFromURL(coverURL);
			else {
				// Local image
				return getBitmapFromLocal(coverURL);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	// get Local image
	private Bitmap getBitmapFromLocal(String localURL){
		try {
			Uri uri = Uri.parse(localURL);
			File file = new File(uri.getPath());
			FileInputStream fileStream = new FileInputStream(file);
			BufferedInputStream buf = new BufferedInputStream(fileStream);
			Bitmap myBitmap = BitmapFactory.decodeStream(buf);
			buf.close();
			return myBitmap;
		} catch (Exception ex) {
			try {
				InputStream fileStream = cordovaActivity.getAssets().open("www/" + localURL);
				BufferedInputStream buf = new BufferedInputStream(fileStream);
				Bitmap myBitmap = BitmapFactory.decodeStream(buf);
				buf.close();
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
			return myBitmap;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
}
