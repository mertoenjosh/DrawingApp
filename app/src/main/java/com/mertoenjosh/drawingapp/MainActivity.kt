package com.mertoenjosh.drawingapp

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private lateinit var flDrawingViewContainer: FrameLayout
    private lateinit var drawingView: DrawingView
    private lateinit var ibBrush: ImageButton
    private lateinit var llPaintColors: LinearLayout
    private lateinit var ibGallery: ImageButton
    private lateinit var ivBackground: ImageView
    private lateinit var ibUndo: ImageButton
    private lateinit var ibSave: ImageButton

    private var mImageButtonCurrentPaint: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        llPaintColors = findViewById(R.id.ll_paint_colors)
        ibBrush = findViewById(R.id.ib_brush)
        ibGallery = findViewById(R.id.ib_gallery)
        ivBackground = findViewById(R.id.iv_background)
        ibUndo = findViewById(R.id.ib_undo)
        ibSave = findViewById(R.id.ib_save)
        flDrawingViewContainer = findViewById(R.id.fl_drawing_view_container)


        drawingView.setSizeForBrush(10.toFloat())

        mImageButtonCurrentPaint = llPaintColors[2] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_active)
        )

        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        ibGallery.setOnClickListener {
            if (isReadStorageAllowed()) {
                // run code to get image from gallery

                val pickPhotoIntent = Intent (Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                startActivityForResult(pickPhotoIntent, GALLERY)
            } else {
                requestStoragePersmission()
            }
        }

        ibUndo.setOnClickListener {
            drawingView.onClickUndo()
        }

        ibSave.setOnClickListener {
            if (isReadStorageAllowed()) {
                BitmapAsyncTask(getBitmapFromView(flDrawingViewContainer)).execute()
            } else {
                requestStoragePersmission()
            }
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GALLERY) {
                try {
                    if (data!!.data != null) {
                        ivBackground.visibility = View.VISIBLE
                        ivBackground.setImageURI(data.data)
                    } else {
                        Toast.makeText(this, "Error in parsing image or is corrupted", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)

        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")

        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)

        smallBtn.setOnClickListener {
            drawingView.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        mediumBtn.setOnClickListener {
            drawingView.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        largeBtn.setOnClickListener {
            drawingView.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view != mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton

            val colorTag = imageButton.tag.toString()
            drawingView.setColor(colorTag)
            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_active))
            mImageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_normal))
            mImageButtonCurrentPaint = view
        }
    }

    private fun requestStoragePersmission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE).toString()
            )) {
            Toast.makeText(this, "Need Permission to add a BG", Toast.LENGTH_SHORT).show()
        }

        ActivityCompat.requestPermissions(this, arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ), STORAGE_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission GRANTED", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission DENIED", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isReadStorageAllowed() :Boolean {
        val result = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(returnedBitmap)

        val bgDrawable = view.background

        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap
    }

    private inner class BitmapAsyncTask(val mBitmap: Bitmap): AsyncTask<Any, Void, String>() {
        private lateinit var mProgressDialog: Dialog

        override fun onPreExecute() {
            super.onPreExecute()
            showProgressDialog()
        }

        override fun doInBackground(vararg params: Any?): String {
            var result = ""

            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir!!.absoluteFile.toString() +
                            File.separator+"KidsDrawingApp_" +
                            System.currentTimeMillis() / 1000 + ".png")
                    val fos = FileOutputStream(f)
                    fos.write(bytes.toByteArray())
                    fos.close()
                    result = f.absolutePath
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }

            return result
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            cancelProgressDialog()

            if (!result!!.isEmpty()) {
                Toast.makeText(this@MainActivity, "File saved successfully: $result", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Something went wrong while saving the file", Toast.LENGTH_SHORT).show()
            }

            MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result), null) {
                path, uri -> val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.type = "image/png"

                startActivity(
                    Intent.createChooser(
                        shareIntent, "Share with: "
                    )
                )

            }
        }

        private fun showProgressDialog() {
            mProgressDialog = Dialog(this@MainActivity)
            mProgressDialog.setContentView(R.layout.dialog_custom_progress)
            mProgressDialog.show()
        }

        private fun cancelProgressDialog() {
            mProgressDialog.dismiss()
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 1
        private const val GALLERY = 2
    }
}