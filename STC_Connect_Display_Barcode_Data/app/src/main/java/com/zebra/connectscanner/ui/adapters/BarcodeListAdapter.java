package com.zebra.connectscanner.ui.adapters;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.zebra.connectscanner.R;
import com.zebra.connectscanner.barcode.BarcodeTypes;
import com.zebra.connectscanner.entities.Barcode;
import java.util.ArrayList;

/**
 * BarcodeListAdapter implemented to add the data to recycle view
 */
public class BarcodeListAdapter extends RecyclerView.Adapter<BarcodeListAdapter.ViewHolder>{
    private ArrayList<Barcode> listBarcodes;

    public BarcodeListAdapter(ArrayList<Barcode> listBarcodes) {
        this.listBarcodes = listBarcodes;
    }
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View listItem= layoutInflater.inflate(R.layout.list_barcode_layout, parent, false);
        ViewHolder viewHolder = new ViewHolder(listItem);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        holder.tv_barCode.setText(new String(listBarcodes.get(position).getBarcodeData()));
        holder.tv_barCodeType.setText(BarcodeTypes.getBarcodeTypeName(listBarcodes.get(0).getBarcodeType()));
        holder.txtBarcodeCounter.setText(Integer.toString(position + 1) );
        holder.txtBarcodeLength.setText(" Characters = "+ listBarcodes.get(0).getBarcodeData().length);
    }


    @Override
    public int getItemCount() {
        return listBarcodes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tv_barCode;
        public TextView tv_barCodeType;
        public TextView txtBarcodeCounter;
        public TextView txtBarcodeLength;

        public ViewHolder(View rowView) {
            super(rowView);
            this.tv_barCode = (TextView) rowView.findViewById(R.id.txt_barcode_data);
            this.tv_barCodeType = (TextView) rowView.findViewById(R.id.txt_barcode_type);
            this.txtBarcodeCounter = (TextView) rowView.findViewById(R.id.txt_barcode_counter);
            this.txtBarcodeLength = (TextView) rowView.findViewById(R.id.txt_barcode_length);

        }
    }
}
