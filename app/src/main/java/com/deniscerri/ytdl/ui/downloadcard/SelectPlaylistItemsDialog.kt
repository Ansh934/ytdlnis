package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.PlaylistAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.setTextAndRecalculateWidth
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

class SelectPlaylistItemsDialog : BottomSheetDialogFragment(), PlaylistAdapter.OnItemClickListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var listAdapter : PlaylistAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var ok: MaterialButton
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fromTextInput: TextInputLayout
    private lateinit var toTextInput: TextInputLayout
    private lateinit var count: TextView
    private lateinit var selectBetween: MenuItem

    private lateinit var items: List<ResultItem?>
    private lateinit var itemURLs: List<String>
    private lateinit var type: DownloadViewModel.Type

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]

        if (Build.VERSION.SDK_INT >= 33){
            arguments?.getParcelableArrayList("results", ResultItem::class.java)
        }else{
            arguments?.getParcelableArrayList<ResultItem>("results")
        }.apply {
            if (this == null){
                dismiss()
                return
            }else{
                items = this
            }
        }
        type = arguments?.getSerializable("type") as DownloadViewModel.Type
    }

    @SuppressLint("RestrictedApi", "NotifyDataSetChanged")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.select_playlist_items, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }


        listAdapter =
            PlaylistAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listAdapter
        recyclerView.enableFastScroll()
        listAdapter.submitList(items)

        count = view.findViewById(R.id.count)
        count.text = "0 ${resources.getString(R.string.selected)}"

        fromTextInput = view.findViewById(R.id.from_textinput)
        toTextInput = view.findViewById(R.id.to_textinput)


        fromTextInput.editText!!.doAfterTextChanged { _text ->
            reset()
            val start = _text.toString()
            val end = toTextInput.editText!!.text.toString()

            if (checkRanges(start, end)) {
                if (start.toInt() < end.toInt()){
                    var startNr = Integer.parseInt(start)
                    startNr--
                    var endNr = Integer.parseInt(end)
                    endNr--
                    if (startNr <= 0) startNr = 0
                    if (endNr > items.size) endNr = items.size - 1
                    listAdapter.checkRange(startNr, endNr)
                    ok.isEnabled = true
                    count.text = "${listAdapter.getCheckedItems().size} ${resources.getString(R.string.selected)}"
                }
            }
        }

        toTextInput.editText!!.doAfterTextChanged { _text  ->
            reset()
            val start = fromTextInput.editText!!.text.toString()
            val end = _text.toString()

            if (checkRanges(start, end)) {
                if (start.toInt() < end.toInt()){
                    var startNr = Integer.parseInt(start)
                    startNr--
                    var endNr = Integer.parseInt(end)
                    endNr--
                    if (startNr <= 0) startNr = 0
                    if (endNr > items.size) endNr = items.size -1
                    listAdapter.checkRange(startNr, endNr)
                    ok.isEnabled = true
                    count.text = "${listAdapter.getCheckedItems().size} ${resources.getString(R.string.selected)}"
                }
            }
        }

        val checkAll = view.findViewById<FloatingActionButton>(R.id.check_all)
        checkAll!!.setOnClickListener {
            if (listAdapter.getCheckedItems().size != items.size){
                fromTextInput.editText!!.setTextAndRecalculateWidth("1")
                toTextInput.editText!!.setTextAndRecalculateWidth(items.size.toString())
                listAdapter.checkAll()
                fromTextInput.isEnabled = true
                toTextInput.isEnabled = true
                ok.isEnabled = true
                count.text = resources.getString(R.string.all_items_selected)
            }else{
                reset()
                fromTextInput.isEnabled = true
                toTextInput.isEnabled = true
                ok.isEnabled = false
                fromTextInput.editText!!.setTextAndRecalculateWidth("")
                toTextInput.editText!!.setTextAndRecalculateWidth("")
            }
        }


        ok = view.findViewById(R.id.bottomsheet_ok_button)
        ok.isEnabled = false
        ok.setOnClickListener {
            ok.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val checkedItems = listAdapter.getCheckedItems()
                val checkedResultItems = items.filter { item -> checkedItems.contains(item!!.url) }
                if (checkedResultItems.size == 1){
                    val resultItem = resultViewModel.getItemByURL(checkedResultItems[0]!!.url)!!
                    withContext(Dispatchers.Main){
                        findNavController().navigate(R.id.action_selectPlaylistItemsDialog_to_downloadBottomSheetDialog, bundleOf(
                            Pair("result", resultItem),
                            Pair("type", downloadViewModel.getDownloadType(type, resultItem.url)),
                        ))
                    }
                }else{
                    val downloadItems = mutableListOf<DownloadItem>()
                    checkedResultItems.forEach { c ->
                        c!!.id = 0
                        val i = downloadViewModel.createDownloadItemFromResult(
                            result = c, givenType = type)
                        if (type == DownloadViewModel.Type.command){
                            i.format = downloadViewModel.getLatestCommandTemplateAsFormat()
                        }
                        downloadItems.add(i)
                    }

                    downloadViewModel.insertToProcessing(downloadItems)

                    withContext(Dispatchers.Main){
                        findNavController().navigate(R.id.action_selectPlaylistItemsDialog_to_downloadMultipleBottomSheetDialog)
                    }
                }

                dismiss()

            }
            true
        }

        val bottomAppBar = view.findViewById<BottomAppBar>(R.id.bottomAppBar)
        bottomAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when(m.itemId) {
                R.id.invert_selected -> {
                    listAdapter.invertSelected(items)
                    val checkedItems = listAdapter.getCheckedItems()
                    if (checkedItems.size == items.size){
                        count.text = resources.getString(R.string.all_items_selected)
                    }else{
                        count.text = "${checkedItems.size} ${resources.getString(R.string.selected)}"
                    }
                    if(checkedItems.isNotEmpty() && checkedItems.size < items.size){
                        fromTextInput.isEnabled = false
                        toTextInput.isEnabled = false
                    }
                    ok.isEnabled = checkedItems.isNotEmpty()
                }
                R.id.select_between -> {
                    val selectedItems = listAdapter.getCheckedItems()
                    if(selectedItems.size != 2){
                        m.isVisible = false
                    }else{
                        val item2 = itemURLs.indexOf(selectedItems.last())
                        val item1 = itemURLs.indexOf(selectedItems.first())
                        if(item1 > item2) listAdapter.checkRange(item2, item1)
                        else listAdapter.checkRange(item1, item2)
                        count.text = "${listAdapter.getCheckedItems().size} ${resources.getString(R.string.selected)}"
                    }
                }
            }
            true
        }

        selectBetween = bottomAppBar.menu.findItem(R.id.select_between)
        itemURLs = items.map { it!!.url }

    }


    private fun checkRanges(start: String, end: String) : Boolean {
        return start.isNotBlank() && end.isNotBlank()
    }

    private fun reset(){
        ok.isEnabled = false
        listAdapter.clearCheckeditems()
        count.text = "0 ${resources.getString(R.string.selected)}"
    }


    override fun onCardSelect(itemURL: String, isChecked: Boolean, checkedItems: List<String>) {
        if (checkedItems.size == items.size){
            count.text = resources.getString(R.string.all_items_selected)
        }else{
            count.text = "${checkedItems.size} ${resources.getString(R.string.selected)}"
        }
        if (checkedItems.isEmpty()){
            ok.isEnabled = false
            fromTextInput.isEnabled = true
            toTextInput.isEnabled = true
        }else{
            ok.isEnabled = true
            fromTextInput.isEnabled = false
            toTextInput.isEnabled = false
            val canSelectBetween = run {
                val size = checkedItems.size
                if(size != 2) false
                else {
                    val item1 = itemURLs.indexOf(checkedItems.first())
                    val item2 = itemURLs.indexOf(checkedItems.last())

                    (item1-item2).absoluteValue > 1
                }
            }
            selectBetween.isVisible = canSelectBetween
        }
    }

}

