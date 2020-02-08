package com.absinthe.kage.adapter;

import androidx.annotation.NonNull;

import com.absinthe.kage.R;
import com.absinthe.kage.device.model.DeviceInfo;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

public class DeviceAdapter extends BaseQuickAdapter<DeviceInfo, BaseViewHolder> {

    public DeviceAdapter() {
        super(R.layout.item_device);
        addChildClickViewIds(R.id.btn_connect);
    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, DeviceInfo deviceInfo) {
        baseViewHolder.setText(R.id.tv_device_name, deviceInfo.getName());
        baseViewHolder.setText(R.id.tv_device_ip, deviceInfo.getIp());
    }
}
