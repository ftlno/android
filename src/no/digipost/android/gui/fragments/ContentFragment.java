/**
 * Copyright (C) Posten Norge AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.digipost.android.gui.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import no.digipost.android.R;
import no.digipost.android.api.LetterOperations;
import no.digipost.android.gui.adapters.ContentArrayAdapter;
import no.digipost.android.utilities.DialogUtitities;

public abstract class ContentFragment extends Fragment {
    ActivityCommunicator activityCommunicator;

    protected Context context;
    protected LetterOperations letterOperations;

    protected ListView listView;
    protected ContentArrayAdapter listAdapter;

    protected ProgressDialog progressDialog;

    public ContentFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        context = getActivity();
        letterOperations = new LetterOperations(context);

        View view = inflater.inflate(R.layout.fragment_layout_listview, container, false);
        listView = (ListView) view.findViewById(R.id.fragment_content_listview);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView parent, View view, int position, long id) {
                activityCommunicator.onListLongClick();
                return true;
            }
        });

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activityCommunicator = (ActivityCommunicator) activity;
    }

    protected void showProgressDialog(final AsyncTask task) {
        progressDialog = DialogUtitities.getProgressDialogWithMessage(context, context.getString(R.string.loading_content));
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.abort), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();
                task.cancel(true);
            }
        });
        progressDialog.show();
    }

    protected void hideProgressDialog() {
        progressDialog.dismiss();
        progressDialog = null;
    }

    public void filterList(String filterQuery) {
        listAdapter.getFilter().filter(filterQuery);
    }

    public abstract void updateAccountMeta();

    public interface ActivityCommunicator {
        public void onStartRefreshContent();
        public void onEndRefreshContent();
        public void onListLongClick();
    }
}