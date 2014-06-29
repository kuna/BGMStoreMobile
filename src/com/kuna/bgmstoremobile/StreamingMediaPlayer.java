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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

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

    private static final int INTIAL_KB_BUFFER =  96*10/8;//assume 96kbps*10secs/8bits per byte

    private Handler h;

    //  Track for display by progressBar
    private int totalKbRead = 0;

    // Create Handler to call View updates on the main UI thread.
    private final Handler handler = new Handler();

    private MediaPlayer mediaPlayer;
    private File downloadingMediaFile;

    private boolean isInterrupted;
    private Context context;
    private int counter = 0;

    private int mediaLengthInSeconds;	// (milisecond) we dont know the value, so we should find it
    private boolean downloaded;
    private boolean isLoop = false;

    public StreamingMediaPlayer(Context  context, Handler h)
    {
        this.h = h;
        this.context = context;

        this.downloaded = false;
    }

    /**
     * Progressivly download the media to a temporary location and update the MediaPlayer as new content becomes available.
     */
    public void startStreaming(final String mediaUrl) throws IOException {
        downloaded = false;

        // 쓰레드 시작
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

        // 파일 URL 주소로 부터 연결
        URLConnection cn = new URL(mediaUrl).openConnection();
        cn.connect();
        InputStream stream = cn.getInputStream();
        if (stream == null) {
            Log.e(getClass().getName(), "Unable to create InputStream for mediaUrl:" + mediaUrl);
        }

        // 캐시 폴더 만들고 .dat파일 생성
        downloadingMediaFile = new File(context.getCacheDir(),"downloadingMedia.dat");

        // 같은 경로에 파일이 존재 하면 그 파일 삭제함.. 캐시메모리 때문
        if (downloadingMediaFile.exists()) {
            downloadingMediaFile.delete();
        }

        // 다시 파일 생성
        FileOutputStream out = new FileOutputStream(downloadingMediaFile);
        byte buf[] = new byte[16384];
        int totalBytesRead = 0, incrementalBytesRead = 0;
        // 캐시 영역에 파일 저장
        do {
            int numread = stream.read(buf);
            if (numread <= 0)
                break;
            out.write(buf, 0, numread);
            totalBytesRead += numread;
            incrementalBytesRead += numread;
            totalKbRead = totalBytesRead/1000;

            testMediaBuffer();
            fireDataLoadUpdate();
        } while (validateNotInterrupted());
        // 파일 전송이 끝나면 종료
        stream.close();
        if (validateNotInterrupted()) {
            fireDataFullyLoaded();
        }
    }

    private boolean validateNotInterrupted() {
        if (isInterrupted) {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
                //mediaPlayer.release();
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
    private void  testMediaBuffer() {
        Runnable updater = new Runnable() {
            public void run() {
                if (mediaPlayer == null) {
                    //  Only create the MediaPlayer once we have the minimum buffered data
                    if ( totalKbRead >= INTIAL_KB_BUFFER) {
                        try {
                            // 받은 파일의 크기가 INTIAL_KB_BUFFER(120) 이상이면 음악파일 재생
                            startMediaPlayer();
                        } catch (Exception e) {
                            Log.e(getClass().getName(), "Error copying buffered conent.", e);
                        }
                    }
                } else if ( mediaPlayer.getDuration() - mediaPlayer.getCurrentPosition() <= 1000 ){
                    //  NOTE:  The media player has stopped at the end so transfer any existing buffered data
                    //  We test for < 1second of data because the media player can stop when there is still
                    //  a few milliseconds of data left to play
                    // 음악 파일을 받다가 끊어지면 음악 재생을 멈춘다
                    transferBufferToMediaPlayer();
                }
            }
        };
        handler.post(updater);
    }

    private void startMediaPlayer() {
        try {
            File bufferedFile = new File(context.getCacheDir(),"playingMedia" + (counter++) + ".dat");

            // We double buffer the data to avoid potential read/write errors that could happen if the
            // download thread attempted to write at the same time the MediaPlayer was trying to read.
            // For example, we can't guarantee that the MediaPlayer won't open a file for playing and leave it locked while
            // the media is playing.  This would permanently deadlock the file download.  To avoid such a deadloack,
            // we move the currently loaded data to a temporary buffer file that we start playing while the remaining
            // data downloads.
            moveFile(downloadingMediaFile,bufferedFile);

            Log.e(getClass().getName(),"Buffered File path: " + bufferedFile.getAbsolutePath());
            Log.e(getClass().getName(),"Buffered File length: " + bufferedFile.length()+"");

            mediaPlayer = createMediaPlayer(bufferedFile);
            // 음악 파일 생성 후 재생
            // We have pre-loaded enough content and started the MediaPlayer so update the buttons & progress meters.
            mediaPlayer.start();
            startPlayProgressUpdater();
            h.obtainMessage(MSG_READY).sendToTarget();
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
                        Log.e(getClass().getName(), "Error in MediaPlayer: (" + what +") with extra (" +extra +")" );
                        return false;
                    }
                });

        //  It appears that for security/permission reasons, it is better to pass a FileDescriptor rather than a direct path to the File.
        //  Also I have seen errors such as "PVMFErrNotSupported" and "Prepare failed.: status=0x1" if a file path String is passed to
        //  setDataSource().  So unless otherwise noted, we use a FileDescriptor here.
        FileInputStream fis = new FileInputStream(mediaFile);
        mPlayer.setDataSource(fis.getFD());
        mPlayer.setLooping(isLoop);
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
            boolean wasPlaying = mediaPlayer.isPlaying();
            int curPosition = mediaPlayer.getCurrentPosition();

            // Copy the currently downloaded content to a new buffered File.  Store the old File for deleting later.
            File oldBufferedFile = new File(context.getCacheDir(),"playingMedia" + counter + ".dat");
            File bufferedFile = new File(context.getCacheDir(),"playingMedia" + (counter++) + ".dat");

            //  This may be the last buffered File so ask that it be delete on exit.  If it's already deleted, then this won't mean anything.  If you want to
            // keep and track fully downloaded files for later use, write caching code and please send me a copy.
            bufferedFile.deleteOnExit();
            moveFile(downloadingMediaFile,bufferedFile);

            // Pause the current player now as we are about to create and start a new one.  So far (Android v1.5),
            // this always happens so quickly that the user never realized we've stopped the player and started a new one
            mediaPlayer.pause();

            // Create a new MediaPlayer rather than try to re-prepare the prior one.
            mediaPlayer = createMediaPlayer(bufferedFile);
            mediaPlayer.seekTo(curPosition);

            //  Restart if at end of prior buffered content or mediaPlayer was previously playing.
            //	NOTE:  We test for < 1second of data because the media player can stop when there is still
            //  a few milliseconds of data left to play
            boolean atEndOfFile = mediaPlayer.getDuration() - mediaPlayer.getCurrentPosition() <= 1000;
            if (wasPlaying || atEndOfFile){
                mediaPlayer.start();
            }

            // Lastly delete the previously playing buffered File as it's no longer needed.
            oldBufferedFile.delete();

        }catch (Exception e) {
            Log.e(getClass().getName(), "Error updating to newly loaded content.", e);
        }
    }

    private void fireDataLoadUpdate() {
        Runnable updater = new Runnable() {
            public void run() {
                h.obtainMessage(MSG_PROGRESS, (int)totalKbRead).sendToTarget();
            }
        };
        handler.post(updater);
    }

    private void fireDataFullyLoaded() {
        Runnable updater = new Runnable() {
            public void run() {
                transferBufferToMediaPlayer();

                // Delete the downloaded File as it's now been transferred to the currently playing buffer file.
                //downloadingMediaFile.delete(); <- we'll provide it for downloading
                downloaded = true;
                h.obtainMessage(MSG_DOWNLOADED).sendToTarget();
                Log.i("StreamingMediaPlayer", "Audio full loaded: " + Integer.toString(totalKbRead) + " Kb read");
            }
        };
        handler.post(updater);
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void startPlayProgressUpdater() {
        mediaLengthInSeconds = mediaPlayer.getDuration();
        //Log.i("DURATION", Integer.toString(mediaPlayer.getDuration()));
        float progress = ((float)mediaPlayer.getCurrentPosition()/mediaLengthInSeconds);
        h.obtainMessage(MSG_PLAYING, (int)(progress*100)).sendToTarget();

        if (mediaPlayer.isPlaying()) {
            Runnable notification = new Runnable() {
                public void run() {
                    startPlayProgressUpdater();
                }
            };
            handler.postDelayed(notification,1000);
        }
    }

    public void interrupt() {
        h.obtainMessage(MSG_INTERRUPTED).sendToTarget();
        isInterrupted = true;
        validateNotInterrupted();
    }

    /**
     *  Move the file in oldLocation to newLocation.
     */
    public void moveFile(File	oldLocation, File	newLocation)
            throws IOException {

        if ( oldLocation.exists( )) {
            BufferedInputStream  reader = new BufferedInputStream( new FileInputStream(oldLocation) );
            BufferedOutputStream  writer = new BufferedOutputStream( new FileOutputStream(newLocation, false));
            try {
                byte[]  buff = new byte[8192];
                int numChars;
                while ( (numChars = reader.read(  buff, 0, buff.length ) ) != -1) {
                    writer.write( buff, 0, numChars );
                }
            } catch( IOException ex ) {
                throw new IOException("IOException when transferring " + oldLocation.getPath() + " to " + newLocation.getPath());
            } finally {
                try {
                    if ( reader != null ){
                        writer.close();
                        reader.close();
                    }
                } catch( IOException ex ){
                    Log.e(getClass().getName(),"Error closing files when transferring " + oldLocation.getPath() + " to " + newLocation.getPath() );
                }
            }
        } else {
            throw new IOException("Old location does not exist when transferring " + oldLocation.getPath() + " to " + newLocation.getPath() );
        }
    }

    public void setSeek(double percent) {
        mediaPlayer.seekTo((int)(mediaLengthInSeconds * percent));
    }

    public void setLoop(boolean loop) {
        isLoop = loop;
        if (mediaPlayer != null)
            mediaPlayer.setLooping(loop);
    }

    public boolean downloadFileTo(String toPath)
    {
        if (!downloaded) {
            return false;
        }

        if (new File(toPath).exists()) {
            return true;
        }

        try {
            File src = new File(context.getCacheDir(),"downloadingMedia.dat");
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
