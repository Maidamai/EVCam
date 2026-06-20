package com.kooo.evcam;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kooo.evcam.camera.CameraManagerHolder;
import com.kooo.evcam.camera.MultiCameraManager;
import com.kooo.evcam.stream.MjpegStreamManager;

/**
 * MJPEG 视频流配置 UI。
 * <p>
 * 复用副屏的鱼眼参数（按摄像头位置）；pan/cover/分辨率/端口/质量为 MJPEG 流独立配置。
 * 启用开关 → {@link MjpegStreamManager#start}；关闭 → {@link MjpegStreamManager#stop}。
 */
public class MjpegStreamAdjustFragment extends Fragment {
    private static final String TAG = "MjpegStreamAdjustFragment";

    private AppConfig appConfig;
    private MjpegStreamManager manager;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private Button backButton, homeButton, applyResolutionButton, saveButton;
    private SwitchMaterial enabledSwitch, linkageSwitch;
    private TextView accessUrlText, clientCountText;
    private Spinner cameraSpinner, rotationSpinner;
    private EditText widthEdit, heightEdit, portEdit;
    private SeekBar qualitySeekbar, panXSeekbar, panYSeekbar, coverScaleSeekbar;
    private TextView qualityValue, panXValue, panYValue, coverScaleValue;
    private SeekBar k1Seekbar, k2Seekbar, zoomSeekbar;
    private TextView k1Value, k2Value, zoomValue;
    private View defaultCameraLayout;

    private int lastClientCount = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appConfig = new AppConfig(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mjpeg_stream_adjust, container, false);
        initViews(view);
        initSpinners();
        loadSettings();
        setupListeners();
        return view;
    }

    private void initViews(View v) {
        backButton = v.findViewById(R.id.btn_back);
        homeButton = v.findViewById(R.id.btn_home);
        enabledSwitch = v.findViewById(R.id.switch_enabled);
        linkageSwitch = v.findViewById(R.id.switch_linkage);
        defaultCameraLayout = v.findViewById(R.id.layout_default_camera);
        accessUrlText = v.findViewById(R.id.tv_access_url);
        clientCountText = v.findViewById(R.id.tv_client_count);
        cameraSpinner = v.findViewById(R.id.spinner_camera);
        rotationSpinner = v.findViewById(R.id.spinner_rotation);
        widthEdit = v.findViewById(R.id.et_width);
        heightEdit = v.findViewById(R.id.et_height);
        portEdit = v.findViewById(R.id.et_port);
        applyResolutionButton = v.findViewById(R.id.btn_apply_resolution);
        saveButton = v.findViewById(R.id.btn_save_apply);
        qualitySeekbar = v.findViewById(R.id.seekbar_quality);
        qualityValue = v.findViewById(R.id.tv_quality_value);
        panXSeekbar = v.findViewById(R.id.seekbar_pan_x);
        panYSeekbar = v.findViewById(R.id.seekbar_pan_y);
        panXValue = v.findViewById(R.id.tv_pan_x_value);
        panYValue = v.findViewById(R.id.tv_pan_y_value);
        coverScaleSeekbar = v.findViewById(R.id.seekbar_cover_scale);
        coverScaleValue = v.findViewById(R.id.tv_cover_scale_value);
        k1Seekbar = v.findViewById(R.id.seekbar_k1);
        k2Seekbar = v.findViewById(R.id.seekbar_k2);
        zoomSeekbar = v.findViewById(R.id.seekbar_zoom);
        k1Value = v.findViewById(R.id.tv_k1_value);
        k2Value = v.findViewById(R.id.tv_k2_value);
        zoomValue = v.findViewById(R.id.tv_zoom_value);
    }

    private void initSpinners() {
        String[] cameras = {"左 (left)", "右 (right)", "前 (front)", "后 (back)"};
        String[] cameraValues = {"left", "right", "front", "back"};
        ArrayAdapter<String> camAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, cameras);
        camAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cameraSpinner.setAdapter(camAdapter);
        cameraSpinner.setTag(cameraValues);

        String[] rotations = {"0°", "90°", "180°", "270°"};
        ArrayAdapter<String> rotAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, rotations);
        rotAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rotationSpinner.setAdapter(rotAdapter);
    }

    private void loadSettings() {
        enabledSwitch.setChecked(appConfig.isMjpegStreamEnabled());
        linkageSwitch.setChecked(appConfig.isMjpegStreamLinkageMode());

        String cam = appConfig.getMjpegStreamCamera();
        String[] vals = (String[]) cameraSpinner.getTag();
        for (int i = 0; i < vals.length; i++) {
            if (vals[i].equals(cam)) { cameraSpinner.setSelection(i); break; }
        }

        widthEdit.setText(String.valueOf(appConfig.getMjpegStreamWidth()));
        heightEdit.setText(String.valueOf(appConfig.getMjpegStreamHeight()));
        portEdit.setText(String.valueOf(appConfig.getMjpegStreamPort()));

        int q = appConfig.getMjpegStreamQuality();
        qualitySeekbar.setProgress(q);
        qualityValue.setText(String.valueOf(q));

        int px = (int) (appConfig.getMjpegStreamPanX() * 100);
        int py = (int) (appConfig.getMjpegStreamPanY() * 100);
        panXSeekbar.setProgress(px);
        panYSeekbar.setProgress(py);
        panXValue.setText(String.valueOf(px));
        panYValue.setText(String.valueOf(py));

        int cs = (int) ((appConfig.getMjpegStreamCoverScale() - 1.0f) * 100);
        coverScaleSeekbar.setProgress(cs);
        coverScaleValue.setText(String.format("%.2f", 1.0f + cs / 100f));

        loadFisheyeParams();
        updateDefaultCameraVisibility();
        // 同步已运行的单例状态
        MjpegStreamManager existing = MjpegStreamManager.getInstance();
        if (existing != null && existing.isRunning()) {
            manager = existing;
            enabledSwitch.setChecked(true);
            accessUrlText.setText("端口: " + appConfig.getMjpegStreamPort());
        }
    }

    /** 联动模式开启时隐藏"默认摄像头"选择；关闭时显示。 */
    private void updateDefaultCameraVisibility() {
        boolean linkage = linkageSwitch.isChecked();
        defaultCameraLayout.setVisibility(linkage ? View.GONE : View.VISIBLE);
    }

    /** 鱼眼参数按当前选中摄像头位置加载。 */
    private void loadFisheyeParams() {
        String pos = currentCameraPos();
        float k1 = appConfig.getFisheyeCorrectionK1(pos);
        float k2 = appConfig.getFisheyeCorrectionK2(pos);
        float zoom = appConfig.getFisheyeCorrectionZoom(pos);
        int rot = appConfig.getFisheyeCorrectionRotation(pos);

        // K1/K2 范围 [-0.5, 0.5]，映射到 [0, 1000]
        k1Seekbar.setProgress((int) ((k1 + 0.5f) * 1000));
        k2Seekbar.setProgress((int) ((k2 + 0.5f) * 1000));
        k1Value.setText(String.format("%.3f", k1));
        k2Value.setText(String.format("%.3f", k2));

        // zoom 范围 [1.0, 4.0]，映射到 [0, 300]
        zoomSeekbar.setProgress((int) ((zoom - 1.0f) * 100));
        zoomValue.setText(String.format("%.2f", zoom));

        rotationSpinner.setSelection(rot / 90);
    }

    private String currentCameraPos() {
        String[] vals = (String[]) cameraSpinner.getTag();
        return vals[cameraSpinner.getSelectedItemPosition()];
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
        });
        homeButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).goToRecordingInterface();
            }
        });

        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            appConfig.setMjpegStreamEnabled(checked);
            if (checked) startStream();
            else stopStream();
        });

        linkageSwitch.setOnCheckedChangeListener((button, checked) -> {
            appConfig.setMjpegStreamLinkageMode(checked);
            updateDefaultCameraVisibility();
            if (manager != null && manager.isRunning()) {
                manager.applyParams();
            }
        });

        cameraSpinner.setOnItemSelectedListener(new SimpleItemListener() {
            @Override public void onSelected(int pos) {
                appConfig.setMjpegStreamCamera(currentCameraPos());
                loadFisheyeParams();  // 切摄像头后刷新鱼眼参数显示
                if (manager != null && manager.isRunning()) {
                    Toast.makeText(requireContext(), "摄像头变更需重启流生效", Toast.LENGTH_SHORT).show();
                }
            }
        });

        applyResolutionButton.setOnClickListener(v -> applyResolution());
        saveButton.setOnClickListener(v -> saveAndApply());

        bindSeekbar(qualitySeekbar, qualityValue, appConfig::setMjpegStreamQuality, String::valueOf);
        bindSeekbar(panXSeekbar, panXValue, v -> appConfig.setMjpegStreamPanX(v / 100f), String::valueOf);
        bindSeekbar(panYSeekbar, panYValue, v -> appConfig.setMjpegStreamPanY(v / 100f), String::valueOf);
        bindSeekbar(coverScaleSeekbar, coverScaleValue,
                v -> appConfig.setMjpegStreamCoverScale(1.0f + v / 100f),
                v -> String.format("%.2f", 1.0f + v / 100f));

        // 鱼眼参数实时写入 AppConfig（按当前摄像头位置）
        k1Seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float v = p / 1000f - 0.5f;
                k1Value.setText(String.format("%.3f", v));
                appConfig.setFisheyeCorrectionK1(currentCameraPos(), v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { applyLiveParams(); }
        });
        k2Seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float v = p / 1000f - 0.5f;
                k2Value.setText(String.format("%.3f", v));
                appConfig.setFisheyeCorrectionK2(currentCameraPos(), v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { applyLiveParams(); }
        });
        zoomSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float v = 1.0f + p / 100f;
                zoomValue.setText(String.format("%.2f", v));
                appConfig.setFisheyeCorrectionZoom(currentCameraPos(), v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { applyLiveParams(); }
        });
        rotationSpinner.setOnItemSelectedListener(new SimpleItemListener() {
            @Override public void onSelected(int pos) {
                appConfig.setFisheyeCorrectionRotation(currentCameraPos(), pos * 90);
                applyLiveParams();
            }
        });
    }

    private void applyResolution() {
        try {
            int w = Integer.parseInt(widthEdit.getText().toString().trim());
            int h = Integer.parseInt(heightEdit.getText().toString().trim());
            appConfig.setMjpegStreamWidth(w);
            appConfig.setMjpegStreamHeight(h);
            if (manager != null && manager.isRunning()) {
                manager.applyParams();
                Toast.makeText(requireContext(), "分辨率已应用", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "已保存，启动后生效", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "分辨率输入无效", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAndApply() {
        try {
            int port = Integer.parseInt(portEdit.getText().toString().trim());
            appConfig.setMjpegStreamPort(port);
        } catch (NumberFormatException ignored) {}
        applyLiveParams();
        Toast.makeText(requireContext(), "已保存并应用", Toast.LENGTH_SHORT).show();
    }

    /** 把当前参数推到正在运行的 manager（热更新，不重启）。 */
    private void applyLiveParams() {
        if (manager != null && manager.isRunning()) {
            manager.applyParams();
        }
    }

    private void startStream() {
        MultiCameraManager cm = CameraManagerHolder.getInstance().getCameraManager();
        if (cm == null) {
            Toast.makeText(requireContext(), "相机管理器未就绪，请先返回主界面", Toast.LENGTH_LONG).show();
            enabledSwitch.setChecked(false);
            return;
        }
        // 用单例，避免与自动拉起的实例冲突
        MjpegStreamManager.stopInstance();
        manager = new MjpegStreamManager(requireContext(), appConfig, currentCameraPos());
        MjpegStreamManager.setInstanceForUi(manager);  // 注册为全局单例
        manager.setClientListener(count -> uiHandler.post(() -> {
            if (count != lastClientCount) {
                lastClientCount = count;
                clientCountText.setText("客户端: " + count);
            }
        }));
        String url = manager.start(cm);
        if (url == null) {
            Toast.makeText(requireContext(), "启动失败，请检查端口或相机", Toast.LENGTH_LONG).show();
            enabledSwitch.setChecked(false);
            manager = null;
            return;
        }
        accessUrlText.setText("端口: " + appConfig.getMjpegStreamPort());
    }

    private void stopStream() {
        MjpegStreamManager.stopInstance();
        manager = null;
        accessUrlText.setText("未启动");
        clientCountText.setText("客户端: 0");
        lastClientCount = -1;
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 离开页面不停止流（后台保活），UI 引用清空
        manager = null;
    }

    // ===== 工具 =====

    private interface IntFunc { void apply(int v); }
    private interface IntStr { String apply(int v); }

    private void bindSeekbar(SeekBar sb, TextView tv, IntFunc saver, IntStr formatter) {
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tv.setText(formatter.apply(p));
                saver.apply(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { applyLiveParams(); }
        });
    }

    private static abstract class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            onSelected(position);
        }
        abstract void onSelected(int position);
    }
}
