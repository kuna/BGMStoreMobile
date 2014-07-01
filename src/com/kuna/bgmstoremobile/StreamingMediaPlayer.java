package com.kuna.bgmstoremobile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Handler;
import android.util.Log;

/**
 * MediaPlayer does not yet support streaming from external URLs so this class provides a pseudo-streaming function
 * by downloading the content incrementally & playing as soon as we get enough audio in our temporary storage.
 */
public class StreamingMediaPlayer {
    public static final int MSG_READY = 1;
    public static final int MSG_PROGRESS = 2;
    public static final int MSG_PLAYING = 3;
    public static final int MSG_DOWNLOADED = 4;
    public static final int MSG_INTERRUPTED = 5;
    public static final int MSG_COMPLETED = 6;

    private static final int INTIAL_KB_BUFFER = 96 * 10 / 8; // assume 96kbps * 10secs / 8bits per byte

    private Handler mPlayerHandler;

    // Track for display by progressBar
    private int mTotalKbRead = 0;

    // Create Handler to call View updates on the main UI thread.
    private final Handler mHandler = new Handler();

    private MediaPlayer mMediaPlayer;
    private File mDownloadingMediaFile;

    private boolean mInterrupted;
    private Context mContext;
    private int mCounter = 0;

    private int mMediaLengthInSeconds; // (milisecond) we dont know the value, so we should find it
    private boolean mDownloaded;
    private boolean mLooping = false;

    public StreamingMediaPlayer(Context context, Handler playerHandler) {
        mPlayerHandler = playerHandler;
        mContext = context;

        mDownloaded = false;
    }

    /**
     * Progressivly download the media to a temporary location and update the MediaPlayer as new content becomes available.
     */
    public void startStreaming(final String mediaUrl) throws IOException {
        mDownloaded = false;

        // �곕젅���쒖옉
        Runnable r = new Runnable() {
            public void run() {
                try {
                    downloadAudioIncrement(mediaUrl);
                } catch (IOException e) {
                    Log.e(getClass().getName(), "Unable to initialize the MediaPlayer for fileUrl=" + mediaUrl, e);
                    return;
                }
            }
        };
        new Thread(r).start();
    }

    /**
     * Download the url stream to a temporary location and then call the setDataSource
     * for that local file
     */
    public void downloadAudioIncrement(String mediaUrl) throws IOException {

        // �뚯씪 URL 二쇱냼濡�遺�꽣 �곌껐
        URLConnection cn = new URL(mediaUrl).openConnection();
        cn.connect();
        InputStream stream = cn.getInputStream();
        if (stream == null) {
            Log.e(getClass().getName(), "Unable to create InputStream for mediaUrl:" + mediaUrl);
        }

        // 罹먯떆 �대뜑 留뚮뱾怨�.dat�뚯씪 �앹꽦
        mDownloadingMediaFile = new File(mContext.getCacheDir(), "downloadingMedia.dat");

        // 媛숈� 寃쎈줈���뚯씪��議댁옱 �섎㈃ 洹��뚯씪 ��젣��. 罹먯떆硫붾え由��뚮Ц
        if (mDownloadingMediaFile.exists()) {
            mDownloadingMediaFile.delete();
        }

        // �ㅼ떆 �뚯씪 �앹꽦
        FileOutputStream out = new FileOutputStream(mDownloadingMediaFile);
        byte buf[] = new byte[16384];
        int totalBytesRead = 0, incrementalBytesRead = 0;
        // 罹먯떆 �곸뿭���뚯씪 ��옣
        do {
            int numread = stream.read(buf);
            if (numread <= 0) {
                break;
            }
            out.write(buf, 0, numread);
            totalBytesRead += numread;
            incrementalBytesRead += numread;
            mTotalKbRead = totalBytesRead / 1000;

            testMediaBuffer();
            fireDataLoadUpdate();
        } while (validateNotInterrupted());
        // �뚯씪 �꾩넚���앸굹硫�醫낅즺
        stream.close();
        if (validateNotInterrupted()) {
            fireDataFullyLoaded();
        }
    }

    private boolean validateNotInterrupted() {
        if (mInterrupted) {
            if (mMediaPlayer != null) {
                mMediaPlayer.pause();
                //mMediaPlayer.release();
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * Test whether we need to transfer buffered data to the MediaPlayer.
     * Interacting with MediaPlayer on non-main UI thread can causes crashes to so perform this using a Handler.
     */
    private void testMediaBuffer() {
        Runnable updater = new Runnable() {
            public void run() {
                if (mMediaPlayer == null) {
                    // Only create the MediaPlayer once we have the minimum buffered data
                    if ( mTotalKbRead >= INTIAL_KB_BUFFER) {
                        try {
                            // 諛쏆� �뚯씪���ш린媛�INTIAL_KB_BUFFER(120) �댁긽�대㈃ �뚯븙�뚯씪 �ъ깮
                            startMediaPlayer();
                        } catch (Exception e) {
                            Log.e(getClass().getName(), "Error copying buffered conent.", e);
                        }
                    }
                } else if ( mMediaPlayer.getDuration() - mMediaPlayer.getCurrentPosition() <= 1000 ) {
                    // NOTE: The media player has stopped at the end so transfer any existing buffered data
                    // We test for < 1second of data because the media player can stop when there is still
                    // a few milliseconds of data left to play
                    // �뚯븙 �뚯씪��諛쏅떎媛��딆뼱吏�㈃ �뚯븙 �ъ깮��硫덉텣��
                    transferBufferToMediaPlayer();
                }
            }
        };
        mHandler.post(updater);
    }

    private void startMediaPlayer() {
        try {
            File bufferedFile = new File(mContext.getCacheDir(), "playingMedia" + (mCounter++) + ".dat");

            // We double buffer the data to avoid potential read/write errors that could happen if the
            // download thread attempted to write at the same time the MediaPlayer was trying to read.
            // For example, we can't guarantee that the MediaPlayer won't open a file for playing and leave it locked while
            // the media is playing. This would permanently deadlock the file download. To avoid such a deadloack,
            // we move the currently loaded data to a temporary buffer file that we start playing while the remaining
            // data downloads.
            moveFile(mDownloadingMediaFile, bufferedFile);

            Log.e(getClass().getName(), "Buffered File path: " + bufferedFile.getAbsolutePath());
            Log.e(getClass().getName(), "Buffered File length: " + bufferedFile.length() + "");

            mMediaPlayer = createMediaPlayer(bufferedFile);
            // �뚯븙 �뚯씪 �앹꽦 ���ъ깮
            // We have pre-loaded enough content and started the MediaPlayer so update the buttons & progress meters.
            mMediaPlayer.start();
            startPlayProgressUpdater();
            mPlayerHandler.obtainMessage(MSG_READY).sendToTarget();
            //playButton.setEnabled(true);
        } catch (IOException e) {
            Log.e(getClass().getName(), "Error initializing the MediaPlayer.", e);
            return;
        }
    }

    private MediaPlayer createMediaPlayer(File mediaFile)
            throws IOException {
        MediaPlayer mPlayer = new MediaPlayer();
        mPlayer.setOnErrorListener(
                new MediaPlayer.OnErrorListener() {
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Log.e(getClass().getName(), "Error in MediaPlayer: (" + what + ") with extra (" + extra + ")");
                        return false;
                    }
                });

        // It appears that for security/permission reasons, it is better to pass a FileDescriptor rather than a direct path to the File.
        // Also I have seen errors such as "PVMFErrNotSupported" and "Prepare failed.: status=0x1" if a file path String is passed to
        // setDataSource(). So unless otherwise noted, we use a FileDescriptor here.
        FileInputStream fis = new FileInputStream(mediaFile);
        mPlayer.setDataSource(fis.getFD());
        if (mDownloaded) {
        	// loop default value: false
        	mPlayer.setLooping(mLooping);
        	
        	// make event handler for onCompleteion
        	if (!mLooping) {
        		mPlayer.setOnCompletionListener(new OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
						mPlayerHandler.obtainMessage(MSG_COMPLETED).sendToTarget();
					}
				});
        	}
        }
        mPlayer.prepare();
        return mPlayer;
    }

    /**
     * Transfer buffered data to the MediaPlayer.
     * NOTE: Interacting with a MediaPlayer on a non-main UI thread can cause thread-lock and crashes so
     * this method should always be called using a Handler.
     */
    private void transferBufferToMediaPlayer() {
        try {
            // First determine if we need to restart the player after transferring data...e.g. perhaps the user pressed pause
            boolean wasPlaying = mMediaPlayer.isPlaying();
            int curPosition = mMediaPlayer.getCurrentPosition();

            // Copy the currently downloaded content to a new buffered File. Store the old File for deleting later.
            File oldBufferedFile = new File(mContext.getCacheDir(), "playingMedia" + mCounter + ".dat");
            File bufferedFile = new File(mContext.getCacheDir(), "playingMedia" + (mCounter++) + ".dat");

            // This may be the last buffered File so ask that it be delete on exit. If it's already deleted, then this won't mean anything. If you want to
            // keep and track fully downloaded files for later use, write caching code and please send me a copy.
            bufferedFile.deleteOnExit();
            moveFile(mDownloadingMediaFile, bufferedFile);

            // Pause the current player now as we are about to create and start a new one. So far (Android v1.5),
            // this always happens so quickly that the user never realized we've stopped the player and started a new one
            mMediaPlayer.pause();

            // Create a new MediaPlayer rather than try to re-prepare the prior one.
            mMediaPlayer = createMediaPlayer(bufferedFile);
            mMediaPlayer.seekTo(curPosition);

            // Restart if at end of prior buffered content or mMediaPlayer was previously playing.
            // NOTE: We test for < 1second of data because the media player can stop when there is still
            // a few milliseconds of data left to play
            boolean atEndOfFile = mMediaPlayer.getDuration() - mMediaPlayer.getCurrentPosition() <= 1000;
            if (wasPlaying || atEndOfFile) {
                mMediaPlayer.start();
            }

            // Lastly delete the previously playing buffered File as it's no longer needed.
            oldBufferedFile.delete();

        } catch (Exception e) {
            Log.e(getClass().getName(), "Error updating to newly loaded content.", e);
        }
    }

    private void fireDataLoadUpdate() {
        Runnable updater = new Runnable() {
            public void run() {
                mPlayerHandler.obtainMessage(MSG_PROGRESS, (int) mTotalKbRead).sendToTarget();
            }
        };
        mHandler.post(updater);
    }

    private void fireDataFullyLoaded() {
        Runnable updater = new Runnable() {
            public void run() {
                mDownloaded = true;
                transferBufferToMediaPlayer();

                // Delete the downloaded File as it's now been transferred to the currently playing buffer file.
                //mDownloadingMediaFile.delete(); <- we'll provide it for downloading
                mPlayerHandler.obtainMessage(MSG_DOWNLOADED).sendToTarget();
                Log.i("StreamingMediaPlayer", "Audio full loaded: " + Integer.toString(mTotalKbRead) + " Kb read");
            }
        };
        mHandler.post(updater);
    }

    public MediaPlayer getMediaPlayer() {
        return mMediaPlayer;
    }

    public void startPlayProgressUpdater() {
        mMediaLengthInSeconds = mMediaPlayer.getDuration();
        //Log.i("DURATION", Integer.toString(mMediaPlayer.getDuration()));
        float progress = ((float) mMediaPlayer.getCurrentPosition() / mMediaLengthInSeconds);
        mPlayerHandler.obtainMessage(MSG_PLAYING, (int) (progress * 100)).sendToTarget();

        if (mMediaPlayer.isPlaying()) {
            Runnable notification = new Runnable() {
                public void run() {
                    startPlayProgressUpdater();
                }
            };
            mHandler.postDelayed(notification, 1000);
        }
    }

    public void interrupt() {
        mPlayerHandler.obtainMessage(MSG_INTERRUPTED).sendToTarget();
        mInterrupted = true;
        validateNotInterrupted();
    }

    /**
     * Move the file in oldLocation to newLocation.
     */
    public void moveFile(File oldLocation, File newLocation)
            throws IOException {

        if ( oldLocation.exists( )) {
            BufferedInputStream reader = new BufferedInputStream( new FileInputStream(oldLocation) );
            BufferedOutputStream writer = new BufferedOutputStream( new FileOutputStream(newLocation, false));
            try {
                byte[] buff = new byte[8192];
                int numChars;
                while ( (numChars = reader.read( buff, 0, buff.length ) ) != -1) {
                    writer.write( buff, 0, numChars );
                }
            } catch ( IOException ex ) {
                throw new IOException("IOException when transferring " + oldLocation.getPath() + " to " + newLocation.getPath());
            } finally {
                try {
                    if ( reader != null ) {
                        writer.close();
                        reader.close();
                    }
                } catch( IOException ex ) {
                    Log.e(getClass().getName(),"Error closing files when transferring " + oldLocation.getPath() + " to " + newLocation.getPath() );
                }
            }
        } else {
            throw new IOException("Old location does not exist when transferring " + oldLocation.getPath() + " to " + newLocation.getPath() );
        }
    }

    public void setSeek(double percent) {
        mMediaPlayer.seekTo((int) (mMediaLengthInSeconds * percent));
    }

    public void setLoop(boolean looping) {
        mLooping = looping;
        if (mMediaPlayer != null) {
            mMediaPlayer.setLooping(mLooping);
        }
    }

    public boolean downloadFileTo(String toPath) {
        if (!mDownloaded) {
            return false;
        }

        if (new File(toPath).exists()) {
            return true;
        }

        try {
            File src = new File(mContext.getCacheDir(), "downloadingMedia.dat");
            FileInputStream inStream = new FileInputStream(src);
            FileOutputStream outStream = new FileOutputStream(toPath);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inStream.close();
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
