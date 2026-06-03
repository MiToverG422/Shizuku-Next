package moe.shizuku.manager.management;

import android.content.pm.PackageInfo;

import moe.shizuku.manager.R;

import java.util.ArrayList;
import java.util.List;

import rikka.recyclerview.BaseRecyclerViewAdapter;
import rikka.recyclerview.ClassCreatorPool;

public class AppsAdapter extends BaseRecyclerViewAdapter<ClassCreatorPool> {

    public AppsAdapter() {
        super();

        getCreatorPool().putRule(PackageInfo.class, AppViewHolder.CREATOR);
        getCreatorPool().putRule(EmptyState.class, EmptyViewHolder.CREATOR);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItemAt(position).hashCode();
    }

    @Override
    public ClassCreatorPool onCreateCreatorPool() {
        return new ClassCreatorPool();
    }

    public void updateData(List<PackageInfo> data) {
        getItems().clear();
        if (data == null || data.isEmpty()) {
            getItems().add(new EmptyState(R.string.home_app_management_empty));
        } else {
            getItems().addAll(data);
        }
        notifyDataSetChanged();
    }

    public void showEmpty(int messageRes) {
        getItems().clear();
        getItems().add(new EmptyState(messageRes));
        notifyDataSetChanged();
    }

    public List<Object> getDataItems() {
        return new ArrayList<>(getItems());
    }
}
