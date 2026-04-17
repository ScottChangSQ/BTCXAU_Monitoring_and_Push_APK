/*
 * 设置主壳页，承接设置首页共享页面控制器。
 */
package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ContentSettingsBinding;
import com.binance.monitor.ui.host.HostTabPage;
import com.binance.monitor.ui.log.LogActivity;

public class SettingsFragment extends Fragment implements HostTabPage {

    private SettingsPageController pageController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View settingsContentView = ((ViewGroup) view).getChildAt(0);
        pageController = new SettingsPageController(new SettingsPageController.Host() {
            @NonNull
            @Override
            public AppCompatActivity requireActivity() {
                return (AppCompatActivity) SettingsFragment.this.requireActivity();
            }

            @Override
            public void openSettingsSection(@NonNull String section, @NonNull String title) {
                Intent intent = new Intent(requireContext(), SettingsSectionActivity.class);
                intent.putExtra(SettingsSectionActivity.EXTRA_SECTION, section);
                intent.putExtra(SettingsSectionActivity.EXTRA_TITLE, title);
                startActivity(intent);
            }

            @Override
            public void openLogPage() {
                startActivity(new Intent(requireContext(), LogActivity.class));
            }
        }, ContentSettingsBinding.bind(settingsContentView));
        pageController.bind();
    }

    @Override
    public void onHostPageShown() {
        if (pageController != null) {
            pageController.onPageShown();
        }
    }

    @Override
    public void onHostPageHidden() {
        if (pageController != null) {
            pageController.onPageHidden();
        }
    }

    @Override
    public void onDestroyView() {
        if (pageController != null) {
            pageController.onDestroy();
            pageController = null;
        }
        super.onDestroyView();
    }
}
