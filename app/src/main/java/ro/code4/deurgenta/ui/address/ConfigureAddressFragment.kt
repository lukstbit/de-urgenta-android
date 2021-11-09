package ro.code4.deurgenta.ui.address

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.SearchView
import android.widget.SimpleCursorAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.here.sdk.mapview.MapView
import com.here.sdk.search.Suggestion
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ro.code4.deurgenta.R
import ro.code4.deurgenta.data.model.MapAddressType
import ro.code4.deurgenta.databinding.FragmentConfigureAddressBinding
import ro.code4.deurgenta.helper.MapViewUtils
import ro.code4.deurgenta.helper.getPermissionStatus
import ro.code4.deurgenta.helper.hideSoftInput
import ro.code4.deurgenta.helper.logD
import ro.code4.deurgenta.helper.logI
import ro.code4.deurgenta.helper.setToRotateIndefinitely
import ro.code4.deurgenta.helper.showPermissionExplanation
import ro.code4.deurgenta.interfaces.ClickButtonCallback
import ro.code4.deurgenta.ui.base.ViewModelFragment

@SuppressLint("LongLogTag")
@Suppress("TooManyFunctions")
class ConfigureAddressFragment : ViewModelFragment<ConfigureAddressViewModel>() {

    override val layout: Int
        get() = R.layout.fragment_configure_address

    override val screenName: Int
        get() = R.string.configure_addresses

    override val viewModel: ConfigureAddressViewModel by viewModel()
    private val sharedPreferences: SharedPreferences by inject()
    private lateinit var mapView: MapView
    private var mapViewUtils: MapViewUtils? = null
    lateinit var viewBinding: FragmentConfigureAddressBinding
    private var mapAddressType: MapAddressType = MapAddressType.HOME
    private var loadingAnimator: ObjectAnimator? = null
    private fun searchView() = viewBinding.appbarSearch.querySearch
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                logI("Access to location was granted", TAG)
                permissionNotice?.dismiss()
                mapViewUtils?.startLocationUpdates()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (getPermissionStatus(
                            requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        logI("Access to location was NOT granted", TAG)
                        showPermissionRequiredNotice()
                    }
                } else {
                    logI("Access to location was NOT granted", TAG)
                    showPermissionRequiredNotice()
                }
            }
        }
    private var permissionNotice: Snackbar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = DataBindingUtil.inflate(inflater, layout, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!sharedPreferences.getBoolean(PREFS_HAS_SEEN_PERMISSION_NOTICE, false)) {
            showPermissionExplanation(requireContext(), sharedPreferences) { initiatePermissionRequest() }
        } else {
            initiatePermissionRequest()
        }
        viewBinding.lifecycleOwner = viewLifecycleOwner
        viewBinding.appbarSearch.toolbarSearch.setOnClickListener {
            findNavController().navigate(R.id.back_to_configure_profile)
        }

        viewModel.saveResult().observe(viewLifecycleOwner, { result ->
            result.handle(onSuccess = {
                val direction =
                    ConfigureAddressFragmentDirections.actionNavigateSaveAddress(mapAddress = it!!)
                findNavController().navigate(direction)
            })
        })

        arguments?.let { bundle ->
            bundle.getParcelable<MapAddressType>("mapAddressType")?.let { it ->
                mapAddressType = it
            }
        }

        arguments?.let { bundle ->
            bundle.getInt("titleResourceId").let { resId ->
                viewBinding.toolbarTitle = getString(resId)
            }
        }

        mapView = viewBinding.mapViewLayout.mapView
        mapView.onCreate(savedInstanceState)
        mapViewUtils = MapViewUtils(mapView, requireActivity(), mapViewCallback)

        initCallbacks()
        initSearchView()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback()
        )
    }

    override fun onResume() {
        super.onResume()
        mapViewUtils?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapViewUtils?.onPause()
    }

    private fun initCallbacks() {
        viewBinding.saveAddressCallback = ClickButtonCallback {
            mapViewUtils?.getCurrentAddress()
                ?.let { mapAddress ->
                    mapAddress.type = mapAddressType
                    viewModel.saveAddress(mapAddress)
                }
        }

        viewBinding.locateMeCallback = ClickButtonCallback {
            searchView().clearFocus()
            setQuery("", false)
            mapViewUtils?.loadLastKnownLocation(true)
        }
    }

    private fun initSearchView() {

        searchView().setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    view?.let { hideSoftInput(it) }
                    mapViewUtils?.searchOnMap(query, MapViewUtils.SearchType.STANDARD)
                    return true
                }

                override fun onQueryTextChange(queryString: String): Boolean {
                    // Hack
                    if (queryString.isNullOrEmpty()) {
                        updateSaveButtonVisibility(View.GONE)
                    }
                    mapViewUtils?.searchOnMap(queryString, MapViewUtils.SearchType.AUTOSUGGEST)
                    return true
                }
            }
        )

        val cursorAdapter = initSearchViewAdapter()

        searchView().suggestionsAdapter = cursorAdapter
        searchView().setOnSuggestionListener(onSuggestionListener())
    }

    private fun initSearchViewAdapter(): CursorAdapter {
        val from = arrayOf(SearchManager.SUGGEST_COLUMN_TEXT_1)
        val to = intArrayOf(R.id.item_label)

        return SimpleCursorAdapter(
            context,
            R.layout.layout_search_item,
            null,
            from,
            to,
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )
    }

    private fun onSuggestionListener() =
        object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return true
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val cursor = searchView().suggestionsAdapter.cursor
                cursor.moveToPosition(position)
                val selection =
                    cursor.getString(cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_TEXT_1))
                setQuery(selection, true)
                return true
            }
        }

    private val mapViewCallback = object : MapViewUtils.MapViewCallback {

        override fun onLoaded() {
            setAsLoading(false)
        }

        override fun onLoading() {
            setAsLoading(true)
        }

        override fun onError(error: MapViewUtils.MapDataError) {
            if (loadingAnimator?.isRunning == true) {
                setAsLoading(false)
            }
            if (error.errorType == MapViewUtils.MapErrorType.PERMISSION_ERROR) {
                try {
                    // ignored
                    // the app itself will handle the location permission
                } catch (sie: IntentSender.SendIntentException) {
                    logI(TAG, "PendingIntent unable to execute request.")
                }
            } else {
                Toast.makeText(requireContext(), getString(error.errorStringId), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        override fun onSuggestError(errorMessage: String) {
            logD("error loading autosuggestion results:$errorMessage", TAG)
        }

        override fun onSearchSuccess() {
            updateSaveButtonVisibility(View.VISIBLE)
        }

        override fun onSuggestSuccess(query: String, suggestions: List<Suggestion>) {
            val cursor = MatrixCursor(arrayOf(BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1))
            query.let {
                suggestions.forEachIndexed { index, suggestion ->
                    logD("autosuggestion text.$suggestion", TAG)
                    if (suggestion.place?.title?.contains(query, true) == true) {
                        cursor.addRow(arrayOf(index, suggestion.place?.title))
                    }
                }
            }

            searchView().suggestionsAdapter.changeCursor(cursor)
        }
    }

    private fun updateSaveButtonVisibility(flag: Int) {
        viewBinding.mapViewLayout.saveAddress.visibility = flag
    }

    fun setQuery(query: String, submit: Boolean) {
        searchView().setQuery(query, submit)
    }

    private fun setAsLoading(isLoading: Boolean) {
        viewBinding.mapViewLayout.mapLoadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            loadingAnimator?.cancel()
            loadingAnimator = viewBinding.mapViewLayout.mapLoadingIndicator.setToRotateIndefinitely()
            loadingAnimator?.start()
        }
    }

    override fun handleOnBackPressedInternal() {
        permissionNotice?.dismiss()
        val directions = ConfigureAddressFragmentDirections.backToConfigureProfile()
        findNavController().navigate(directions)
    }

    private fun showPermissionRequiredNotice() {
        permissionNotice =
            Snackbar.make(viewBinding.root, getString(R.string.permission_required_notice), Snackbar.LENGTH_INDEFINITE)
        val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.fromParts(SCHEME_PACKAGE, requireActivity().packageName, null)
        }
        if (appSettingsIntent.resolveActivity(requireActivity().packageManager) != null) {
            permissionNotice?.setAction(getString(R.string.permission_btn_settings)) {
                startActivity(appSettingsIntent)
            }
        }
        permissionNotice?.show()
    }

    private fun initiatePermissionRequest() {
        if (getPermissionStatus(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    companion object {
        const val TAG: String = "ConfigureAccountFragment"
        const val SCHEME_PACKAGE = "package"
        const val PREFS_HAS_SEEN_PERMISSION_NOTICE = "prefs_has_seen_permission_notice"
    }
}
