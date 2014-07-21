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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.apache.http.Header;

import java.util.ArrayList;

import javax.ws.rs.core.HttpHeaders;

import no.digipost.android.DigipostApplication;
import no.digipost.android.R;
import no.digipost.android.api.ContentOperations;
import no.digipost.android.api.exception.DigipostApiException;
import no.digipost.android.api.exception.DigipostAuthenticationException;
import no.digipost.android.api.exception.DigipostClientException;
import no.digipost.android.authentication.Secret;
import no.digipost.android.constants.ApiConstants;
import no.digipost.android.constants.ApplicationConstants;
import no.digipost.android.documentstore.DocumentContentStore;
import no.digipost.android.gui.MainContentActivity;
import no.digipost.android.gui.adapters.AttachmentArrayAdapter;
import no.digipost.android.gui.adapters.DocumentArrayAdapter;
import no.digipost.android.gui.adapters.FolderArrayAdapter;
import no.digipost.android.gui.content.HtmlAndReceiptActivity;
import no.digipost.android.gui.content.MuPDFActivity;
import no.digipost.android.gui.content.UnsupportedDocumentFormatActivity;
import no.digipost.android.model.Attachment;
import no.digipost.android.model.Document;
import no.digipost.android.model.Documents;
import no.digipost.android.model.Folder;
import no.digipost.android.utilities.DialogUtitities;
import no.digipost.android.utilities.JSONUtilities;
import no.digipost.android.utilities.NetworkUtilities;

import static android.app.Activity.RESULT_OK;

public class DocumentFragment extends ContentFragment {

    protected AttachmentArrayAdapter attachmentAdapter;
    protected FolderArrayAdapter folderAdapter;
    private AsyncHttpClient asyncHttpClient;
    private int content = 0;
    private Dialog folderDialog;
    private Dialog attachmentDialog;
    private boolean openAttachment = true;

    public DocumentFragment() {
    }

    public DocumentFragment(int content) {
        this.content = content;
    }

    @Override
    public int getContent() {
        return content;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        super.listView.setMultiChoiceModeListener(new MultiChoiceModeListener());
        super.listAdapter = new DocumentArrayAdapter(getActivity(), R.layout.content_list_item);
        super.listView.setAdapter(listAdapter);
        super.listView.setOnItemClickListener(new DocumentListOnItemClickListener());
        updateAccountMeta();
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == MainContentActivity.INTENT_REQUESTCODE) {
                if (data.hasExtra(ApiConstants.FRAGMENT_ACTIVITY_RESULT_ACTION)) {

                    String action = data.getStringExtra(ApiConstants.FRAGMENT_ACTIVITY_RESULT_ACTION);

                    if (action.equals(ApiConstants.MOVE)) {
                        String toLocation = data.getStringExtra(ApiConstants.FRAGMENT_ACTIVITY_RESULT_LOCATION);
                        String folderId = data.getStringExtra(ApiConstants.FRAGMENT_ACTIVITY_RESULT_FOLDERID);
                        executeDocumentMoveTask(DocumentContentStore.getDocumentParent(), toLocation, folderId);

                    } else if (action.equals(ApiConstants.DELETE)) {
                        deleteDocument(DocumentContentStore.getDocumentParent());
                    }
                }
            }
        }

        DocumentContentStore.clearContent();
    }

    private void showMoveToFolderDialog() {
        folderDialog = null;
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.attachmentdialog_layout, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()).setNegativeButton(getString(R.string.abort),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );

        builder.setView(view);

        ListView moveToFolderListView = (ListView) view.findViewById(R.id.attachmentdialog_listview);

        ArrayList<Folder> folders = getMoveFolders();
        folderAdapter = new FolderArrayAdapter(getActivity(), R.layout.attachmentdialog_list_item, folders);
        moveToFolderListView.setAdapter(folderAdapter);
        moveToFolderListView.setOnItemClickListener(new MoveToFolderListOnItemClickListener());

        builder.setTitle(getString(R.string.move_to));
        folderDialog = builder.create();
        folderDialog.show();
    }

    private ArrayList<Folder> getMoveFolders() {
        ArrayList<Folder> moveLocations = new ArrayList<Folder>();

        if (MainContentActivity.folders != null) {
            if (MainContentActivity.fragmentName != null) {


                if (MainContentActivity.folders != null) {

                    //Mapper
                    ArrayList<Folder> folders = new ArrayList<Folder>();
                    for (Folder f : MainContentActivity.folders) {
                        if (!MainContentActivity.fragmentName.equals(f.getName())) {
                            folders.add(f);
                        }
                    }

                    //Postkassen
                    if (folders.size() != MainContentActivity.folders.size()) {
                        Folder postkassen = new Folder();
                        postkassen.setName(getString(R.string.drawer_inbox));
                        moveLocations.add(0, postkassen);
                    }

                    for (Folder f : folders) {
                        moveLocations.add(f);
                    }
                }
            }
            return moveLocations;
        } else {
            return null;
        }
    }

    private void showAttachmentDialog(final Document document, int listPosition) {
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.attachmentdialog_layout, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()).setNegativeButton(getString(R.string.close),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        builder.setView(view);

        ListView attachmentListView = (ListView) view.findViewById(R.id.attachmentdialog_listview);
        attachmentAdapter = new AttachmentArrayAdapter(getActivity(), R.layout.attachmentdialog_list_item, document.getAttachment());
        attachmentListView.setAdapter(attachmentAdapter);
        attachmentListView.setOnItemClickListener(new AttachmentListOnItemClickListener(document, listPosition));

        builder.setTitle(attachmentAdapter.getMainSubject());
        attachmentDialog = builder.create();
        attachmentDialog.show();

    }

    private void openListItem(final Document document, int listPosition) {
        if (document.getAuthenticationLevel().equals(ApiConstants.AUTHENTICATION_LEVEL_TWO_FACTOR)) {
            showTwoFactorDialog();
        } else if (document.getAttachment().size() == 1 && document.getAttachment().get(0).getOpeningReceiptUri() != null) {
            showOpeningReceiptDialog(document, document.getAttachment().get(0), listPosition, 0);
        } else {
            findDocumentAttachments(document, listPosition);
        }
    }

    private void findDocumentAttachments(final Document document, int listPosition) {
        ArrayList<Attachment> attachments = document.getAttachment();

        if (attachments.size() > 1) {
            showAttachmentDialog(document, listPosition);
        } else {
            Attachment attachment = document.getAttachment().get(0);
            getAttachmentContent(document, 0, listPosition, attachment);
        }
    }

    private void sendOpeningReceipt(final Document document, final Attachment attachment, int listPosition, int attachmentPosition) {
        SendOpeningReceiptTask task = new SendOpeningReceiptTask(document, attachment, listPosition, attachmentPosition);
        task.execute();
    }

    private void showOpeningReceiptDialog(final Document document, final Attachment attachment, final int listPosition, final int attachmentPosition) {

        String title = getString(R.string.dialog_opening_receipt_title);
        String message = getString(R.string.dialog_opening_receipt_message);

        AlertDialog.Builder builder = DialogUtitities.getAlertDialogBuilderWithMessageAndTitle(context, message, title);

        builder.setPositiveButton(getString(R.string.dialog_opening_receipt_yes), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int id) {
                sendOpeningReceipt(document, attachment, listPosition, attachmentPosition);
                dialog.dismiss();
            }
        }).setCancelable(false).setNegativeButton(getString(R.string.abort), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int id) {
                dialog.cancel();
            }
        });

        builder.create().show();
    }

    private void showTwoFactorDialog() {

        String message = getString(R.string.dialog_error_two_factor_message);
        String title = getString(R.string.dialog_error_two_factor_title);

        AlertDialog.Builder builder = DialogUtitities.getAlertDialogBuilderWithMessageAndTitle(context, message, title);

        builder.setNegativeButton(R.string.abort, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        builder.create().show();
    }

    private void openAttachmentContent(final Attachment attachment) {
        String fileType = attachment.getFileType();
        Intent intent;

        if (fileType.equals(ApiConstants.FILETYPE_PDF)) {
            intent = new Intent(context, MuPDFActivity.class);
        } else if (fileType.equals(ApiConstants.FILETYPE_HTML)) {
            intent = new Intent(context, HtmlAndReceiptActivity.class);
        } else {
            intent = new Intent(context, UnsupportedDocumentFormatActivity.class);
        }

        if (attachment.getType().equals(ApiConstants.INVOICE) && attachment.getInvoice() != null) {
            intent.putExtra(INTENT_SEND_TO_BANK, true);
        }

        intent.putExtra(INTENT_CONTENT, getContent());
        startActivityForResult(intent, MainContentActivity.INTENT_REQUESTCODE);
    }

    protected void showContentProgressDialog(String message) {
        progressDialog = DialogUtitities.getProgressDialogWithMessage(context, message);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.abort), new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                dialog.dismiss();
                openAttachment = false;
                asyncHttpClient.cancelRequests(context, true);
            }
        });

        progressDialog.show();
    }

    private void getAttachmentContent(final Document parentDocument, final int attachmentListPosition, final int documentListPosition, final Attachment attachment) {

        if (parentDocument != null && attachment != null) {
            asyncHttpClient = new AsyncHttpClient();
            asyncHttpClient.addHeader(HttpHeaders.USER_AGENT, DigipostApplication.USER_AGENT);
            asyncHttpClient.addHeader(ApiConstants.ACCEPT, ApiConstants.CONTENT_OCTET_STREAM);
            asyncHttpClient.addHeader(ApiConstants.AUTHORIZATION, ApiConstants.BEARER + Secret.ACCESS_TOKEN);

            asyncHttpClient.get(context, attachment.getContentUri(), new AsyncHttpResponseHandler() {

                @Override
                public void onStart() {
                    super.onStart();
                    openAttachment = true;
                    if (!progressDialogIsVisible) {
                        showContentProgressDialog(context.getString(R.string.loading_content));
                    }
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    if (openAttachment) {
                        DocumentContentStore.setContent(responseBody, parentDocument, attachmentListPosition);
                        DocumentContentStore.setMoveFolders(getMoveFolders());
                        openAttachmentContent(attachment);
                        updateAdapterDocument(parentDocument, documentListPosition);

                        ArrayList<Attachment> attachments = parentDocument.getAttachment();

                        if (attachments.size() > 1) {
                            attachmentAdapter.setAttachments(attachments);
                        }
                        activityCommunicator.onUpdateAccountMeta();
                    }

                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    openAttachment = false;
                    String errorMessage = getString(R.string.error_digipost_api);

                    if (statusCode == NetworkUtilities.HTTP_STATUS_UNAUTHORIZED) {
                        errorMessage = getString(R.string.error_invalid_token);
                    } else if (statusCode == NetworkUtilities.HTTP_STATUS_BAD_REQUEST) {
                        errorMessage = getString(R.string.error_bad_request);
                    } else if (statusCode == NetworkUtilities.HTTP_STATUS_INTERNAL_SERVER_ERROR) {
                        errorMessage = getString(R.string.error_digipost_api);
                    }

                    DialogUtitities.showToast(DocumentFragment.this.getActivity(), errorMessage);
                    updateAccountMeta();
                }

                @Override
                public void onCancel() {
                    super.onCancel();
                    openAttachment = false;
                }

                @Override
                public void onFinish() {
                    super.onFinish();
                    hideProgressDialog();
                }
            });
        }
    }

    @Override
    public void updateAccountMeta() {
        if (getContent() > ApplicationConstants.numberOfStaticFolders) {
            if (getContent() - ApplicationConstants.numberOfStaticFolders == MainContentActivity.numberOfFolders) {
                content = ApplicationConstants.MAILBOX;
            }
        }
        GetDocumentMetaTask task = new GetDocumentMetaTask(getContent());
        task.execute();
    }

    private void setEmptyViewText() {
        int contentType = getContent();
        int textResource = contentType == ApplicationConstants.MAILBOX ? R.string.emptyview_mailbox : R.string.emptyview_folder;
        setListEmptyViewText(getString(textResource), null);
    }

    protected void executeDocumentMoveTask(Document document, String toLocation, String folderId) {
        ArrayList<Document> documents = new ArrayList<Document>();

        if (document != null) {
            documents.add(document);
        } else {
            documents = listAdapter.getCheckedItems();
            contentActionMode.finish();
        }

        DocumentMoveTask documentMoveTask = new DocumentMoveTask(documents, toLocation, folderId);
        documentMoveTask.execute();
    }

    protected void moveDocument(String toLocation, String folderId) {
        executeDocumentMoveTask(null, toLocation, folderId);
    }

    protected void deleteDocument(Document document) {
        ArrayList<Object> documents = new ArrayList<Object>();
        documents.add(document);

        ContentDeleteTask contentDeleteTask = new ContentDeleteTask(documents);
        contentDeleteTask.execute();
    }

    private void updateAdapterDocument(Document document, int listPosition) {
        listAdapter.replaceAtPosition(document, listPosition);
    }

    private class MultiChoiceModeListener extends ContentMultiChoiceModeListener {

        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            super.onCreateActionMode(actionMode, menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, android.view.MenuItem menuItem) {
            super.onActionItemClicked(actionMode, menuItem);
            switch (menuItem.getItemId()) {
                case R.id.main_context_menu_delete:
                    DocumentFragment.super.deleteContent();
                    break;
                case R.id.main_context_menu_move:
                    showMoveToFolderDialog();
                    break;
            }

            return true;
        }
    }

    private class DocumentListOnItemClickListener implements AdapterView.OnItemClickListener {
        public void onItemClick(final AdapterView<?> arg0, final View view, final int position, final long arg3) {
            openListItem((Document) DocumentFragment.super.listAdapter.getItem(position), position);
        }
    }

    private class MoveToFolderListOnItemClickListener implements AdapterView.OnItemClickListener {
        public MoveToFolderListOnItemClickListener() {

        }

        public void onItemClick(final AdapterView<?> arg0, final View arg1, final int position, final long arg3) {

            Folder folder = folderAdapter.getItem(position);
            int folderId = folder.getId();
            String location;

            if (folderId == 0) {
                location = "INBOX";
            } else {
                location = "FOLDER";
            }

            moveDocument(location, folderId + "");
            if (folderDialog != null) {
                folderDialog.dismiss();
                folderDialog = null;
            }
        }
    }

    private class AttachmentListOnItemClickListener implements AdapterView.OnItemClickListener {
        private Document parentDocument;
        private int parentListPosition;

        public AttachmentListOnItemClickListener(Document parentDocument, int parentListPosition) {
            this.parentDocument = parentDocument;
            this.parentListPosition = parentListPosition;
        }

        public void onItemClick(final AdapterView<?> arg0, final View arg1, final int position, final long arg3) {
            Attachment attachment = attachmentAdapter.getItem(position);
            if (attachment.getOpeningReceiptUri() != null) {
                showOpeningReceiptDialog(parentDocument, attachment, parentListPosition, position);
            } else {
                getAttachmentContent(parentDocument, position, parentListPosition, attachment);
            }
        }
    }

    protected class GetDocumentMetaTask extends AsyncTask<Void, Void, Documents> {
        private final int content;
        private String errorMessage;
        private boolean invalidToken;

        public GetDocumentMetaTask(final int content) {
            this.content = content;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            activityCommunicator.onStartRefreshContent();
        }

        @Override
        protected Documents doInBackground(final Void... params) {
            try {
                return ContentOperations.getAccountContentMetaDocument(context, content);
            } catch (DigipostApiException e) {
                Log.e(getClass().getName(), e.getMessage(), e);
                errorMessage = e.getMessage();
                return null;
            } catch (DigipostClientException e) {
                Log.e(getClass().getName(), e.getMessage(), e);
                errorMessage = e.getMessage();
                return null;
            } catch (DigipostAuthenticationException e) {
                Log.e(getClass().getName(), e.getMessage(), e);
                errorMessage = e.getMessage();
                invalidToken = true;
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Documents documents) {
            super.onPostExecute(documents);
            if (isAdded()) {
                DocumentFragment.super.taskIsRunning = false;
                if (documents != null) {
                    ArrayList<Document> docs = documents.getDocument();
                    DocumentFragment.super.listAdapter.replaceAll(docs);
                    if (!docs.isEmpty()) {
                        DocumentFragment.super.setListEmptyViewNoNetwork(false);
                    } else {
                        if (!isDetached()) {
                            setEmptyViewText();
                        }
                    }
                } else {
                    if (invalidToken) {
                        activityCommunicator.requestLogOut();
                    } else if (listAdapter.isEmpty()) {
                        DocumentFragment.super.setListEmptyViewNoNetwork(true);
                    }
                    DialogUtitities.showToast(DocumentFragment.this.context, errorMessage);
                }

                activityCommunicator.onUpdateAccountMeta();
                activityCommunicator.onEndRefreshContent();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            activityCommunicator.onEndRefreshContent();
        }
    }

    private class DocumentMoveTask extends AsyncTask<Void, Document, String> {
        private ArrayList<Document> documents;
        private String toLocation;
        private String folderId;
        private boolean invalidToken;
        private int progress;

        public DocumentMoveTask(ArrayList<Document> documents, String toLocation, String folderId) {
            this.documents = documents;
            this.toLocation = toLocation;
            this.folderId = folderId;
            this.progress = 0;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            DocumentFragment.super.showContentProgressDialog(this, "");
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                for (Document document : documents) {
                    if (!isCancelled()) {
                        publishProgress(document);
                        progress++;
                        document.setLocation(toLocation);
                        document.setFolderId(folderId);
                        ContentOperations.moveDocument(context, document);
                    }
                }

                return null;
            } catch (DigipostAuthenticationException e) {
                Log.e(getClass().getName(), e.getMessage(), e);
                invalidToken = true;
                return e.getMessage();
            } catch (DigipostApiException e) {
                Log.e(getClass().getName(), e.getMessage(), e);
                return e.getMessage();
            } catch (DigipostClientException e) {
                Log.e(getClass().getName(), e.getMessage(), e);
                return e.getMessage();
            }
        }

        @Override
        protected void onProgressUpdate(Document... values) {
            super.onProgressUpdate(values);

            DocumentFragment.super.progressDialog.setMessage("Flytter " + values[0].getSubject() + " (" + progress + "/" + documents.size()
                    + ")");
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            DocumentFragment.super.hideProgressDialog();
            DialogUtitities.showToast(context, progress + " av " + documents.size() + " ble flyttet.");
            updateAccountMeta();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (isAdded()) {
                DocumentFragment.super.taskIsRunning = false;
                DocumentFragment.super.hideProgressDialog();

                if (result != null) {
                    DialogUtitities.showToast(context, result);

                    if (invalidToken) {
                        activityCommunicator.requestLogOut();
                    }
                } else {
                    if (attachmentDialog != null) {
                        attachmentDialog.dismiss();
                        attachmentDialog = null;
                    }
                }
                updateAccountMeta();
            }
        }
    }

    protected class SendOpeningReceiptTask extends AsyncTask<Void, Void, Boolean> {
        private String errorMessage;
        private Document document;
        private Attachment attachment;
        private boolean invalidToken;
        private int listPosition, attachmentPosition;

        public SendOpeningReceiptTask(final Document document, final Attachment attachment, int listPosition, int attachmentPosition) {
            invalidToken = false;
            this.document = document;
            this.attachment = attachment;
            this.listPosition = listPosition;
            this.attachmentPosition = attachmentPosition;
        }


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!DocumentFragment.super.progressDialogIsVisible) {
                DocumentFragment.super.showContentProgressDialog(this, context.getString(R.string.loading_content));
            }
            DocumentFragment.super.progressDialogIsVisible = true;
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            try {
                document = (Document) JSONUtilities.processJackson(Document.class, ContentOperations.sendOpeningReceipt(context, attachment));
                attachment = document.getAttachment().get(attachmentPosition);

                return true;
            } catch (DigipostApiException e) {
                errorMessage = e.getMessage();
                return false;
            } catch (DigipostClientException e) {
                errorMessage = e.getMessage();
                return false;
            } catch (DigipostAuthenticationException e) {
                invalidToken = true;
                errorMessage = e.getMessage();
                return false;
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            DocumentFragment.super.hideProgressDialog();
            DocumentContentStore.clearContent();
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            super.onPostExecute(result);
            if (isAdded()) {
                if (result) {
                    getAttachmentContent(document, attachmentPosition, listPosition, attachment);
                } else {
                    if (invalidToken) {
                        activityCommunicator.requestLogOut();
                    }

                    DialogUtitities.showToast(DocumentFragment.this.getActivity(), errorMessage);
                }
            }
        }
    }
}