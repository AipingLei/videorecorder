package jp.co.cyberagent.android.gpuimage.filter.expand.magic;

import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterContext;
import jp.co.cyberagent.android.gpuimage.util.OpenGLUtils;
import android.content.Context;
import android.opengl.GLES20;

import com.blue.librecord.R;

public class MagicEarlyBirdFilter extends GPUImageFilterContext{

	@Override
	public String toString() {
		return "EarlyBird";
	}
	private int[] inputTextureHandles = {-1,-1,-1,-1,-1};
	private int[] inputTextureUniformLocations = {-1,-1,-1,-1,-1};
	
	public MagicEarlyBirdFilter(Context context){
		super(context,OpenGLUtils.readShaderFromRawResource(context, R.raw.earlybird));
	}
	
	public void onDestroy() {
        super.onDestroy();
        GLES20.glDeleteTextures(inputTextureHandles.length, inputTextureHandles, 0);
        for(int i = 0; i < inputTextureHandles.length; i++)
        	inputTextureHandles[i] = -1;
    }
	
	protected void onDrawArraysAfter(){
		for(int i = 0; i < inputTextureHandles.length
				&& inputTextureHandles[i] != OpenGLUtils.NO_TEXTURE; i++){
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + (i+3));
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		}
	}
	  
	protected void onDrawArraysPre(){
		for(int i = 0; i < inputTextureHandles.length 
				&& inputTextureHandles[i] != OpenGLUtils.NO_TEXTURE; i++){
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + (i+3) );
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureHandles[i]);
			GLES20.glUniform1i(inputTextureUniformLocations[i], (i+3));
		}
	}
	
	public void onInit(){
		super.onInit();
		for(int i=0; i < inputTextureUniformLocations.length; i++){
			inputTextureUniformLocations[i] = GLES20.glGetUniformLocation(getProgram(), "inputImageTexture"+(2+i));
		}
	}
	
	public void onInitialized(){
		super.onInitialized();
	    runOnDraw(new Runnable(){
		    public void run(){
		    	inputTextureHandles[0] = OpenGLUtils.loadTextureFromR(mContext, R.drawable.earlybirdcurves);
				inputTextureHandles[1] = OpenGLUtils.loadTextureFromR(mContext, R.drawable.earlybirdoverlaymap_new);
				inputTextureHandles[2] = OpenGLUtils.loadTextureFromR(mContext, R.drawable.vignettemap_new);
				inputTextureHandles[3] = OpenGLUtils.loadTextureFromR(mContext, R.drawable.earlybirdblowout);
				inputTextureHandles[4] = OpenGLUtils.loadTextureFromR(mContext, R.drawable.earlybirdmap);
		    }
	    });
	}
}
