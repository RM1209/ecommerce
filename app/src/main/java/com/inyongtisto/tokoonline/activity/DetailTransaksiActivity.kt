package com.inyongtisto.tokoonline.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.drjacky.imagepicker.ImagePicker
import com.google.gson.Gson
import com.inyongtisto.myhelper.base.BaseActivity
import com.inyongtisto.myhelper.extension.showErrorDialog
import com.inyongtisto.myhelper.extension.showSuccessDialog
import com.inyongtisto.myhelper.extension.toGone
import com.inyongtisto.myhelper.extension.toMultipartBody
import com.inyongtisto.tokoonline.MainActivity
import com.inyongtisto.tokoonline.R
import com.inyongtisto.tokoonline.adapter.AdapterProdukTransaksi
import com.inyongtisto.tokoonline.app.ApiConfig
import com.inyongtisto.tokoonline.helper.Helper
import com.inyongtisto.tokoonline.model.DetailTransaksi
import com.inyongtisto.tokoonline.model.ResponModel
import com.inyongtisto.tokoonline.model.Transaksi
import com.ontbee.legacyforks.cn.pedant.SweetAlert.SweetAlertDialog
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_detail_transaksi.*
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.android.synthetic.main.view_upload.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File


class DetailTransaksiActivity : BaseActivity() {

    var transaksi = Transaksi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_transaksi)
        Helper().setToolbar(this, toolbar, "Detail Transaksi")

        val json = intent.getStringExtra("transaksi")
        transaksi = Gson().fromJson(json, Transaksi::class.java)

        setData(transaksi)
        displayProduk(transaksi.details)
        mainButton()
    }

    private fun mainButton() {
        btn_batal.setOnClickListener {
            SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                    .setTitleText("Apakah anda yakin?")
                    .setContentText("Transaksi akan di batalkan dan tidak bisa di kembalikan!")
                    .setConfirmText("Yes, Batalkan")
                    .setConfirmClickListener {
                        it.dismissWithAnimation()
                        batalTransaksi()
                    }
                    .setCancelText("Tutup")
                    .setCancelClickListener {
                        it.dismissWithAnimation()
                    }.show()
        }

        btn_bayar.setOnClickListener {
                imagePic()
        }
    }

    private fun imagePic(){
        ImagePicker.with(this)
                .crop()
                .maxResultSize(512, 512)
                .createIntentFromDialog { launcher.launch(it) }

    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val uri = it.data?.data!!
            // Use the uri to load the image
            Log.d("TAG", "URI IMAGE: "+uri)
            val fileUri: Uri = uri
            dialogUpload(File(fileUri.path!!))
        }
    }

    var alertDialog : AlertDialog? = null

    @SuppressLint("InflateParams")
    private fun dialogUpload(file: File) {

        val view = layoutInflater
        val layout = view.inflate(R.layout.view_upload, null)

        val imageView: ImageView = layout.findViewById(R.id.image)
        val btnUpload: Button = layout.findViewById(R.id.btn_upload)
        val btnImage: Button = layout.findViewById(R.id.btn_image)

        Picasso.get()
                .load(file)
                .into(imageView)

        btnUpload.setOnClickListener {
            upload(file)
        }

        btnImage.setOnClickListener {
            imagePic()
        }
        alertDialog = AlertDialog.Builder(this).create()
        alertDialog!!.setView(layout)
        alertDialog!!.setCancelable(true)
        alertDialog!!.show()
    }

    private fun upload(file: File){
        progress.show()
        val fileImage = file.toMultipartBody()

        ApiConfig.instanceRetrofit.uploadBukti(transaksi.id, fileImage!!).enqueue(object : Callback<ResponModel> {
            override fun onFailure(call: Call<ResponModel>, t: Throwable) {
                progress.dismiss()
                showErrorDialog(t.message!!)
            }

            override fun onResponse(call: Call<ResponModel>, response: Response<ResponModel>) {
                progress.dismiss()
                if (response.isSuccessful) {
                    if (response.body()!!.success == 1){
                        showSuccessDialog("Upload bukti berhasil") {
                            alertDialog!!.dismiss()
                            btn_bayar.toGone()
                            tv_status.text = "DIBAYAR"
                            onBackPressed()
                        }
                    } else {
                        showErrorDialog(response.message())
                    }

                } else {
                    showErrorDialog(response.body()!!.message)
                }
            }
        })

    }


    fun batalTransaksi() {
        val loading = SweetAlertDialog(this@DetailTransaksiActivity, SweetAlertDialog.PROGRESS_TYPE)
        loading.setTitleText("Loading...").show()
        ApiConfig.instanceRetrofit.batalChekout(transaksi.id).enqueue(object : Callback<ResponModel> {
            override fun onFailure(call: Call<ResponModel>, t: Throwable) {
                loading.dismiss()
                error(t.message.toString())
            }

            override fun onResponse(call: Call<ResponModel>, response: Response<ResponModel>) {
                loading.dismiss()
                val res = response.body()!!
                if (res.success == 1) {
                    SweetAlertDialog(this@DetailTransaksiActivity, SweetAlertDialog.SUCCESS_TYPE)
                            .setTitleText("Success...")
                            .setContentText("Transaksi berhasil dibatalakan")
                            .setConfirmClickListener {
                                it.dismissWithAnimation()
                                onBackPressed()
                            }
                            .show()

//                    Toast.makeText(this@DetailTransaksiActivity, "Transaksi berhasil di batalkan", Toast.LENGTH_SHORT).show()
//                    onBackPressed()
//                    displayRiwayat(res.transaksis)
                } else {
                    error(res.message)
                }
            }
        })
    }

    fun error(pesan: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                .setTitleText("Oops...")
                .setContentText(pesan)
                .show()
    }

    fun setData(t: Transaksi) {
        tv_status.text = t.status

        val formatBaru = "dd MMMM yyyy, kk:mm:ss"
        tv_tgl.text = Helper().convertTanggal(t.created_at, formatBaru)

        tv_penerima.text = t.name + " - " + t.phone
        tv_alamat.text = t.detail_lokasi
        tv_kodeUnik.text = Helper().gantiRupiah(t.kode_unik)
        tv_totalBelanja.text = Helper().gantiRupiah(t.total_harga)
        tv_ongkir.text = Helper().gantiRupiah(t.ongkir)
        tv_total.text = Helper().gantiRupiah(t.total_transfer)

        if (t.status != "MENUNGGU") div_footer.visibility = View.GONE

        var color = getColor(R.color.menungu)
        if (t.status == "SELESAI") color = getColor(R.color.selesai)
        else if (t.status == "BATAL") color = getColor(R.color.batal)

        tv_status.setTextColor(color)
    }

    fun displayProduk(transaksis: ArrayList<DetailTransaksi>) {
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        rv_produk.adapter = AdapterProdukTransaksi(transaksis)
        rv_produk.layoutManager = layoutManager
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

}
