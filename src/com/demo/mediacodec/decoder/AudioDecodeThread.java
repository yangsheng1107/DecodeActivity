package com.demo.mediacodec.decoder;

import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.util.Log;

public class AudioDecodeThread extends Thread {
	private static final String TAG = "AudioDecodeThread";
	private static final boolean DEBUG = false;
	private static final long TIMEOUT = 10000;
	private MediaExtractor extractor;
	private MediaCodec decoder;
	private AudioTrack audioTrack;
	private int sampleRate;
	private int channelCount;	
	private boolean running;
	private String file;
	private long startMs;
	private ByteBuffer[] inputBuffers;
	private ByteBuffer[] outputBuffers;
	private BufferInfo info;

	public AudioDecodeThread(String file) {
		this.file = file;
		this.running = false;

		initDecoder();
		startDecoder();
	}

	private void startDecoder() {
		decoder.start();
		audioTrack.play();
		running = true;
	}

	private void initDecoder() {
		extractor = new MediaExtractor();
		extractor.setDataSource(file);

		for (int i = 0; i < extractor.getTrackCount(); i++) {
			MediaFormat format = extractor.getTrackFormat(i);
			String mime = format.getString(MediaFormat.KEY_MIME);

			if (mime.startsWith("audio/")) {
				int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
				int bufferSize;
				extractor.selectTrack(i);
				decoder = MediaCodec.createDecoderByType(mime);
				decoder.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
				sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
				channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
				switch (channelCount) {
				case 1:
					channelConfig = AudioFormat.CHANNEL_OUT_MONO;
					break;
				case 2:
					channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
					break;
				case 6:
					channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
					break;
				}
				
				bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
				audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
						AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
						

				break;
			}
		}

		if (decoder == null) {
			Log.e(TAG, "Can't find audio info!");
			return;
		}
	}

	public void close() {
		this.running = false;
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
			outputBuffers = decoder.getOutputBuffers();
			break;
		case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
			if (DEBUG)
				Log.d(TAG, "New format " + decoder.getOutputFormat());
			audioTrack.setPlaybackRate(sampleRate);
			break;
		case MediaCodec.INFO_TRY_AGAIN_LATER:
			if (DEBUG)
				Log.d(TAG, "dequeueOutputBuffer timed out!");
			break;
		default:
			ByteBuffer buffer = outputBuffers[outIndex];
			// Log.v("DecodeActivity", "We can't use this buffer but
			// render it due to the API limit, " + buffer);

			// We use a very simple clock to keep the video FPS, or the
			// video
			// playback will be too fast
			final byte[] chunk = new byte[info.size];
			buffer.get(chunk);
			buffer.clear();

			if (chunk.length > 0) {
				// play
				audioTrack.write(chunk, 0, chunk.length);
			}
			decoder.releaseOutputBuffer(outIndex, true);
			break;
		}
	}

	@Override
	public void run() {
		boolean isEOS = false;
		info = new BufferInfo();
		inputBuffers = decoder.getInputBuffers();
		outputBuffers = decoder.getOutputBuffers();

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
		audioTrack.flush();
		audioTrack.release();
		decoder.stop();
		decoder.release();
		extractor.release();
	}
}