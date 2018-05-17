package com.daliborjelnek.touchlessBrowser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.opengl.Visibility;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * Created by Dalibor Jelínek on 04.04.2018.
 */

public class PreviewDialog extends Dialog implements
        android.view.View.OnClickListener {


    public Button yes, no;
    public Activity ownerActivity;
    private ImageView mImageView;
    Animation rotateAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.rotate);
    private ImageView loadingImageView;

    public PreviewDialog(Activity a) {
        super(a);
        ownerActivity = a;
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.custom_modal);
        yes = (Button) findViewById(R.id.btn_yes);
        no = (Button) findViewById(R.id.btn_no);
        mImageView = findViewById(R.id.imageView);
        loadingImageView = findViewById(R.id.imageView3);
        loadingImageView.startAnimation(rotateAnimation);

        yes.setOnClickListener(this);
        no.setOnClickListener(this);

    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_yes:
                Toast.makeText(getContext(), "not implemented yet =(", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_no:
                dismiss();
                break;
            default:
                break;
        }
        dismiss();
    }

    public void setPreviewBitmap(Bitmap bmp){

        if(bmp!=null)
            mImageView.setImageBitmap(bmp);
    }
    public  void showLoading(){

        loadingImageView.setVisibility(View.VISIBLE);
        mImageView.setVisibility(View.INVISIBLE);
        loadingImageView.startAnimation(rotateAnimation);

    }
    public  void showPreview() {

                loadingImageView.setVisibility(View.INVISIBLE);
                mImageView.setVisibility(View.VISIBLE);
                loadingImageView.clearAnimation();

    }




}
