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

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import no.digipost.android.R;
import no.digipost.android.api.exception.DigipostApiException;
import no.digipost.android.api.exception.DigipostAuthenticationException;
import no.digipost.android.api.exception.DigipostClientException;
import no.digipost.android.gui.adapters.LetterArrayAdapter;
import no.digipost.android.model.Letter;
import no.digipost.android.utilities.DialogUtitities;

public abstract class DocumentFragment extends ContentFragment {

    public DocumentFragment(){
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        super.listAdapter = new LetterArrayAdapter(getActivity(), R.layout.content_list_item);
        super.listView.setAdapter(listAdapter);
        return view;
    }

    protected void updateAccountMeta(int content){
        GetAccountMetaTask task = new GetAccountMetaTask(content);
        task.execute();
    }

    protected class GetAccountMetaTask extends AsyncTask<Void, Void, ArrayList<Letter>> {
        private final int content;
        private String errorMessage = "";

        public GetAccountMetaTask(final int content) {
            this.content = content;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            activityCommunicator.onStartRefreshContent();
        }

        @Override
        protected ArrayList<Letter> doInBackground(final Void... params) {
            try {
                return DocumentFragment.super.letterOperations.getAccountContentMeta(content);
            } catch (DigipostApiException e) {
                errorMessage = e.getMessage();
                return null;
            } catch (DigipostClientException e) {
                errorMessage = e.getMessage();
                return null;
            } catch (DigipostAuthenticationException e) {
                errorMessage = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(final ArrayList<Letter> letters) {
            super.onPostExecute(letters);
            if(letters != null){
                DocumentFragment.super.listAdapter.replaceAll(letters);
            } else {
                DialogUtitities.showToast(DocumentFragment.this.context, errorMessage);
            }

            activityCommunicator.onEndRefreshContent();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            activityCommunicator.onEndRefreshContent();
        }
    }
}
