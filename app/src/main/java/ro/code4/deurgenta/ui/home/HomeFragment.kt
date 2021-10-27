package ro.code4.deurgenta.ui.home

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import ro.code4.deurgenta.R
import ro.code4.deurgenta.databinding.FragmentHomeBinding
import ro.code4.deurgenta.ui.base.BaseFragment

class HomeFragment : BaseFragment(R.layout.fragment_home) {

    private val binding: FragmentHomeBinding by viewBinding(FragmentHomeBinding::bind)

    override val screenName: Int
        get() = R.string.analytics_title_home

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding.homeActions) {
            homeMenuAddress.setOnClickListener { /* TODO not yet available */ }
            homeMenuGroup.setOnClickListener {
                findNavController().navigate(HomeFragmentDirections.actionNavHomeToNavItemGroups())
            }
            homeMenuBagpack.setOnClickListener {
                findNavController().navigate(HomeFragmentDirections.actionNavHomeToNavItemBackpacks())
            }
            homeMenuCourses.setOnClickListener {
                findNavController().navigate(HomeFragmentDirections.actionNavHomeToNavItemCourses())
            }
        }
    }
}
