package com.demo.mediacodec.decoder;

import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.util.Log;
import android.view.Surface;

public class VideoDecodeThread extends Thread {
	private static final String TAG = "VideoDecodeThread";
	private static final boolean DEBUG = false;
	private static final long TIMEOUT = 10000;
	private MediaExtractor extractor;
	private MediaCodec decoder;
	private Surface surface;
	private boolean running;
	private String file;
	private BufferInfo info;
	private long startMs;
	private ByteBuffer[] inputBuffers;

	public VideoDecodeThread(Surface surface, String file) {
		this.surface = surface;
		this.file = file;
		this.running = false;

		initDecoder();
		startDecoder();
	}

	private void startDecoder() {
		decoder.start();
		running = true;
	}

	private int initDecoder() {
		extractor = new MediaExtractor();
		extractor.setDataSource(file);

		for (int i = 0; i < extractor.getTrackCount(); i++) {
			MediaFormat format = extractor.getTrackFormat(i);
			String mime = format.getString(MediaFormat.KEY_MIME);

			if (mime.startsWith("video/")) {
				extractor.selectTrack(i);
				decoder = MediaCodec.createDecoderByType(mime);
				decoder.configure(format, surface, null, 0);
				break;
			}
		}

		if (decoder == null) {
			Log.e(TAG, "Can't find video info!");
			return -1;
		}
		return 0;
	}

	public void close() {
		this.running = false;
	}

	@Override
	public void run() {
		boolean isEOS = false;
		info = new BufferInfo();
		inputBuffers = decoder.getInputBuffers();
		startMs = System.currentTimeMillis();
		while (running == true) {
			if (!isEOS) {
				int inIndex = decoder.dequeueInputBuffer(TIMEOUT);
				if (inIndex >= 0) {
					ByteBuffer buffer = inputBuffers[inIndex];
					int sampleSize = extractor.readSampleData(buffer, 0 /* offset */);
					if (sampleSize < 0) {
						if (DEBUG)
							Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
						decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						isEOS = true;
					} else {
						if (DEBUG)
							Log.d(TAG, "sampleSize: " + sampleSize);
						decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
						nextSample();
					}
				}
			}

			processDequeueBuffer();

			// All decoded frames have been rendered, we can stop playing
			// now
			if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
				if (DEBUG)
					Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
				break;
			}
		}
		running = false;
		decoder.stop();
		decoder.release();
		extractor.release();

	}

	private void nextSample() {
		try {
			long sleepTime = (info.presentationTimeUs / 1000) - (System.currentTimeMillis() - startMs);
			if (sleepTime > 0)
				Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		extractor.advance();
	}

	private void processDequeueBuffer() {
		int outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT);
		switch (outIndex) {
		case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
			if (DEBUG)
				Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
			decoder.getOutputBuffers();
			break;
		case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
			if (DEBUG)
				Log.d(TAG, "New format " + decoder.getOutputFormat());
			break;
		case MediaCodec.INFO_TRY_AGAIN_LATER:
			if (DEBUG)
				Log.d(TAG, "dequeueOutputBuffer timed out!");
			break;
		default:
			decoder.releaseOutputBuffer(outIndex, true);
			break;
		}
	}
}
