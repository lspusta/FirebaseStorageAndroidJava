package itkido.me.androidfirebasestoragejava;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.bumptech.glide.load.resource.bitmap.TransformationUtils.rotateImage;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final int GALLERY_REQUEST_CODE = 3;
    private Button btnUploadPhoto;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto);

        btnUploadPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(Intent.ACTION_PICK);
                // Sets the type as image/*. This ensures only components of type image are selected
                intent.setType("image/*");
                //We pass an extra array with the accepted mime types. This will ensure only components with these MIME types as targeted.
                String[] mimeTypes = {"image/jpeg", "image/png"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES,mimeTypes);
                // Launching the Intent
                startActivityForResult(intent,GALLERY_REQUEST_CODE);
            }
        });
    }


    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {


        super.onActivityResult(requestCode, resultCode, data);


            if (requestCode == GALLERY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
                Uri selectedImage = data.getData();
                Glide.with(getApplicationContext()).asBitmap().load(selectedImage).into(new SimpleTarget<Bitmap>() {
                    @Override public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        /* now you have Bitmap resource */
                        savePictureFile(resource, "picturefile");
                    }
                });



            }


    }

    private void savePictureFile(Bitmap bitmap, String photoName){

        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        //File file = new File(directory, photoName + "@resized" + ".jpg");
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        File file = new File(directory, photoName + ts + ".jpg");

        if (!file.exists()) {
            Log.d("path", file.toString());
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, fos);
                fos.flush();
                fos.close();

                File imgFile = new File(file.toString());
                if(imgFile.exists()){
                    Bitmap rotatedBitmap = null;
                    // Bitmap myBitmap = BitmapFactory.decodeFile(file.toString());

                    ExifInterface ei = null;
                    try {
                        ei = new ExifInterface(file.getAbsolutePath());
                        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED);


                        switch(orientation) {

                            case ExifInterface.ORIENTATION_ROTATE_90:
                                rotatedBitmap = rotateImage(bitmap, 90);
                                break;

                            case ExifInterface.ORIENTATION_ROTATE_180:
                                rotatedBitmap = rotateImage(bitmap, 180);
                                break;

                            case ExifInterface.ORIENTATION_ROTATE_270:
                                rotatedBitmap = rotateImage(bitmap, 270);
                                break;

                            case ExifInterface.ORIENTATION_NORMAL:
                            default:
                                rotatedBitmap = bitmap;
                        }



                        Log.d("path", file.toString());
                        try {
                            fos = new FileOutputStream(file);
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, fos);
                            fos.flush();
                            fos.close();
                            uploadFileToFirestorage(file.toString());
                        } catch (java.io.IOException e) {
                            e.printStackTrace();
                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                }


            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void uploadFileToFirestorage(String fileLocation) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        // Create a storage reference from our app
        StorageReference storageRef = storage.getReference();

        UploadTask uploadTask;

        Uri file = Uri.fromFile(new File(fileLocation));

        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();

        final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("Uploading...");
        progressDialog.show();
        String filename = file.getLastPathSegment() + ts;

        final StorageReference firebaseStorageRef = storageRef.child("images/" + filename);
        uploadTask = firebaseStorageRef.putFile(file);

        uploadTask.addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot taskSnapshot) {
                double progress = (100.0*taskSnapshot.getBytesTransferred()/taskSnapshot
                        .getTotalByteCount());
                progressDialog.setMessage("Uploading "+(int)progress+"%");
            }
        });

        uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }

                // Continue with the task to get the download URL
                //Log.d(TAG, "onSuccess: Download Url = " + firebaseStorageRef.getDownloadUrl());
                return firebaseStorageRef.getDownloadUrl();

            }
        })
                .addOnCompleteListener(new OnCompleteListener<Uri>() {
                    @Override
                    public void onComplete(@NonNull Task<Uri> task) {
                        if (task.isSuccessful()) {
                            progressDialog.dismiss();
                            Uri downloadUri = task.getResult();
                            Log.d(TAG, "onSuccess: Download Url = " + downloadUri);
                            Toast.makeText(MainActivity.this, "File Uploaded", Toast.LENGTH_SHORT).show();
                        } else {
                            // Handle failures
                            // ...
                            progressDialog.dismiss();

                        }
                    }
                });
    }

}