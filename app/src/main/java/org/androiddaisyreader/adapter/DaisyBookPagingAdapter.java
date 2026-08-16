package org.androiddaisyreader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.naofum.androiddaisyreader.R;

import org.androiddaisyreader.model.DaisyBookInfo;

/**
 * RecyclerView 用の DaisyBookInfo アダプター。
 * ListAdapter (DiffUtil) を使用して効率的な差分更新を行う。
 */
public class DaisyBookPagingAdapter
        extends ListAdapter<DaisyBookInfo, DaisyBookPagingAdapter.BookViewHolder> {

    private final OnBookClickListener listener;

    public interface OnBookClickListener {
        void onBookClick(DaisyBookInfo book, int position);
    }

    private static final DiffUtil.ItemCallback<DaisyBookInfo> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<DaisyBookInfo>() {
                @Override
                public boolean areItemsTheSame(@NonNull DaisyBookInfo oldItem,
                                               @NonNull DaisyBookInfo newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DaisyBookInfo oldItem,
                                                  @NonNull DaisyBookInfo newItem) {
                    return oldItem.getTitle().equals(newItem.getTitle())
                            && safeEquals(oldItem.getPath(), newItem.getPath())
                            && safeEquals(oldItem.getAuthor(), newItem.getAuthor());
                }

                private boolean safeEquals(String a, String b) {
                    if (a == null) return b == null;
                    return a.equals(b);
                }
            };

    public DaisyBookPagingAdapter(OnBookClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        DaisyBookInfo book = getItem(position);
        if (book != null) {
            holder.bind(book, listener, position);
        }
    }

    static class BookViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtTitle;
        private final TextView txtAuthor;
        private final TextView txtDate;
        private final TextView txtPublisher;
        private final View viewAuthor;
        private final View viewDate;
        private final View viewPublisher;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.daisy_book_title);
            txtAuthor = itemView.findViewById(R.id.daisy_book_author);
            txtDate = itemView.findViewById(R.id.daisy_book_date);
            txtPublisher = itemView.findViewById(R.id.daisy_book_publisher);
            viewAuthor = itemView.findViewById(R.id.daisy_book_view_author);
            viewDate = itemView.findViewById(R.id.daisy_book_view_date);
            viewPublisher = itemView.findViewById(R.id.daisy_book_view_publisher);
        }

        void bind(DaisyBookInfo book, OnBookClickListener listener, int position) {
            // Title
            String title = book.getTitle();
            txtTitle.setText(title != null ? title : "");

            // Author
            String author = book.getAuthor();
            if (author != null && !author.isEmpty()) {
                txtAuthor.setText(author);
                viewAuthor.setVisibility(View.VISIBLE);
            } else {
                txtAuthor.setText("");
                viewAuthor.setVisibility(View.GONE);
            }

            // Date
            String date = book.getDate();
            if (date != null && !date.isEmpty()) {
                txtDate.setText(date);
                viewDate.setVisibility(View.VISIBLE);
            } else {
                txtDate.setText("");
                viewDate.setVisibility(View.GONE);
            }

            // Publisher
            String publisher = book.getPublisher();
            if (publisher != null && !publisher.isEmpty()) {
                txtPublisher.setText(publisher);
                viewPublisher.setVisibility(View.VISIBLE);
            } else {
                txtPublisher.setText("");
                viewPublisher.setVisibility(View.GONE);
            }

            // Click
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookClick(book, position);
                }
            });
        }
    }
}
