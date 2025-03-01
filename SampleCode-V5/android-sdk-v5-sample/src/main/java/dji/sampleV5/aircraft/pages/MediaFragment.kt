package dji.sampleV5.aircraft.pages

import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.recyclerview.widget.GridLayoutManager
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.data.MEDIA_FILE_DETAILS_STR
import dji.sampleV5.aircraft.models.MediaVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sdk.keyvalue.value.camera.CameraStorageLocation
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.datacenter.media.MediaFile
import dji.v5.manager.datacenter.media.MediaFileListState
import kotlinx.android.synthetic.main.frag_media_page.btn_delete
import kotlinx.android.synthetic.main.frag_media_page.btn_disable_playback
import kotlinx.android.synthetic.main.frag_media_page.btn_download
import kotlinx.android.synthetic.main.frag_media_page.btn_enable_playback
import kotlinx.android.synthetic.main.frag_media_page.btn_format
import kotlinx.android.synthetic.main.frag_media_page.btn_get_xmp_custom_info
import kotlinx.android.synthetic.main.frag_media_page.btn_refresh_file_list
import kotlinx.android.synthetic.main.frag_media_page.btn_select
import kotlinx.android.synthetic.main.frag_media_page.btn_set_xmp_custom_info
import kotlinx.android.synthetic.main.frag_media_page.btn_take_photo
import kotlinx.android.synthetic.main.frag_media_page.fetchCount
import kotlinx.android.synthetic.main.frag_media_page.fetch_progress
import kotlinx.android.synthetic.main.frag_media_page.mediaIndex
import kotlinx.android.synthetic.main.frag_media_page.media_recycle_list
import kotlinx.android.synthetic.main.frag_media_page.sp_choose_component
import kotlinx.android.synthetic.main.frag_media_page.sp_choose_storage
import kotlinx.android.synthetic.main.frag_media_page.tv_get_list_state
import kotlinx.android.synthetic.main.frag_media_page.tv_list_count
import kotlinx.android.synthetic.main.frag_media_page.tv_playback


/**
 * @author feel.feng
 * @time 2022/04/19 5:04 下午
 * @description:  回放下载操作界面
 */
class MediaFragment : DJIFragment() {
    private val mediaVM: MediaVM by activityViewModels()
    var adapter: MediaListAdapter? = null

    private var isload = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.frag_media_page, container, false);
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initData()
    }

    private fun initData() {

        if (!isload) {
            mediaVM.init()
            isload = true
        }
        // mediaFileListData 通过 mediaVM.pullMediaFileListFromCamera(mediaFileIndex, fetchCount) 更新
        adapter = MediaListAdapter(mediaVM.mediaFileListData.value?.data!!, ::onItemClick)
        media_recycle_list.adapter = adapter
        // 通过 observe 方法监听 mediaFileListData 的变化，当数据发生变化时更新 adapter 的数据。
        mediaVM.mediaFileListData.observe(viewLifecycleOwner) {
            adapter!!.notifyDataSetChanged()
            tv_list_count.text = "Count:${it.data.size}"
        }


        mediaVM.fileListState.observe(viewLifecycleOwner) {
            if (it == MediaFileListState.UPDATING) {
                fetch_progress?.visibility = View.VISIBLE
            } else {
                fetch_progress?.visibility = View.GONE
            }

            tv_get_list_state?.text = "State:\n ${it.name}"
        }

        mediaVM.isPlayBack.observe(viewLifecycleOwner) {
            tv_playback.text = "isPlayingBack : ${it}"
        }

    }

    private fun initView() {
        media_recycle_list.layoutManager = GridLayoutManager(context, 3)
        btn_delete.setOnClickListener {
            val mediafiles = ArrayList<MediaFile>()
            if (adapter?.getSelectedItems()?.size != 0) {
                mediafiles.addAll(adapter?.getSelectedItems()!!)
                MediaDataCenter.getInstance().mediaManager.deleteMediaFiles(mediafiles, object :
                    CommonCallbacks.CompletionCallback {
                    override fun onSuccess() {
                        clearSelectFiles()
                        ToastUtils.showToast("delete success ");
                    }

                    override fun onFailure(error: IDJIError) {
                        ToastUtils.showToast("delete failed  " + error.description());
                    }

                })
            } else {
                ToastUtils.showToast("please select files ");
            }

        }
        // 获取文件列表
        btn_refresh_file_list.setOnClickListener() {
            val fetchCount = if (TextUtils.isEmpty(fetchCount.text.toString())) {
                -1  //all
            } else {
                fetchCount.text.toString().toInt()
            }

            val mediaFileIndex = if (TextUtils.isEmpty(mediaIndex.text.toString())) {
                -1 //start fetch index
            } else {
                mediaIndex.text.toString().toInt()
            }
            // 从摄像头中获取指定数量和从指定索引开始的媒体文件,mediaFileListData 会更新
            mediaVM.pullMediaFileListFromCamera(mediaFileIndex, fetchCount)
            var isloada = false
        }

        btn_select.setOnClickListener {
            if (adapter == null) {
                return@setOnClickListener
            }
            adapter?.selectionMode = !adapter?.selectionMode!!

            clearSelectFiles()  // SPF：注释和上边else
            btn_select.setText(
                if (adapter?.selectionMode!!) {
                    R.string.unselect_files
                } else {
                    R.string.select_files
                }
            )
            updateDeleteBtn(adapter?.selectionMode!!)
        }

        btn_download.setOnClickListener {
            val mediafiles = ArrayList<MediaFile>()
            if (adapter?.getSelectedItems()?.size != 0)
                mediafiles.addAll(adapter?.getSelectedItems()!!)
                mediaVM.downloadMediaFile(mediafiles)

        }

        btn_set_xmp_custom_info.setOnClickListener {
            dji.sampleV5.aircraft.keyvalue.KeyValueDialogUtil.showInputDialog(
                activity, "xmp custom info",
                "MSDK_V5", "", false
            ) {
                it?.let {
                    mediaVM.setMediaFileXMPCustomInfo(it)
                }
            }
        }

        btn_get_xmp_custom_info.setOnClickListener {
            mediaVM.getMediaFileXMPCustomInfo()
        }

        sp_choose_component.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, index: Int, p3: Long) {

                mediaVM.setComponentIndex(ComponentIndexType.find(index))
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                //do nothing
            }
        }

        sp_choose_storage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, index: Int, p3: Long) {
                mediaVM.setStorage(CameraStorageLocation.find(index))
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                //do nothing
            }
        }

        btn_enable_playback.setOnClickListener {
            mediaVM.enable()
        }

        btn_disable_playback.setOnClickListener {
            mediaVM.disable()
        }

        btn_take_photo.setOnClickListener {
            mediaVM.takePhoto(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("take photo success")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("take photo failed")
                }
            })
        }
        btn_format.setOnClickListener {
            mediaVM.formatSDCard(object :CommonCallbacks.CompletionCallback{
                override fun onSuccess() {
                    ToastUtils.showToast("format SDCard success")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("format SDCard failed ${error.errorCode()}" )
                }

            })

        }
    }

    private fun updateDeleteBtn(enable: Boolean) {
        btn_delete.isEnabled = enable
    }

    private fun clearSelectFiles() {
        adapter?.mSelectedItems?.clear()
        adapter?.notifyDataSetChanged()
    }

    private fun onItemClick(mediaFile: MediaFile, view: View) {

        ViewCompat.setTransitionName(view, mediaFile.fileName);

        val extra = FragmentNavigatorExtras(
            view to "tansitionImage"
        )

        Navigation.findNavController(view).navigate(
            R.id.media_details_page, bundleOf(
                MEDIA_FILE_DETAILS_STR to mediaFile
            ), null, extra
        )

    }

    override fun onDestroyView() {
        super.onDestroyView()
        MediaDataCenter.getInstance().mediaManager.stopPullMediaFileListFromCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaVM.destroy()
        if (mediaVM.isPlayBack.value == true) {
            mediaVM.disable()
        }
        adapter = null
    }

    fun take_photo(){
        mediaVM.takePhoto(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                dji.sampleV5.aircraft.util.ToastUtils.showToast("take photo success")
            }

            override fun onFailure(error: IDJIError) {
                dji.sampleV5.aircraft.util.ToastUtils.showToast("take photo failed")
            }
        })
    }

}