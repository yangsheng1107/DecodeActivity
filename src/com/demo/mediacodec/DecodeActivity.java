package com.demo.mediacodec;

import com.demo.mediacodec.decoder.AudioDecodeThread;
import com.demo.mediacodec.decoder.VideoDecodeThread;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

// https://github.com/taehwandev/MediaCodecExample/tree/master/src/net/thdev/mediacodecexample/decoder
// https://github.com/vecio/MediaCodecDemo
// https://github.com/starlightslo/Android-Music-MediaCodec-Example/blob/master/src/com/tonyhuang/example/Decoder.java
public class DecodeActivity extends Activity implements SurfaceHolder.Callback {
	private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/video.mp4";
	private VideoDecodeThread mVideo = null;
	private AudioDecodeThread mAudio = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SurfaceView sv = new SurfaceView(this);
		sv.getHolder().addCallback(this);
		setContentView(sv);
	}

	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (mVideo == null) {
			mVideo = new VideoDecodeThread(holder.getSurface(), SAMPLE);
			mVideo.start();
		}

		if (mAudio == null) {
			mAudio = new AudioDecodeThread(SAMPLE);
			mAudio.start();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mVideo != null) {
			mVideo.close();
			mVideo = null;
		}
		if (mAudio != null) {
			mAudio.close();
			mAudio = null;
		}

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}