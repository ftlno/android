/**
 * Copyright (C) Posten Norge AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.digipost.android.api;

import java.util.ArrayList;

import no.digipost.android.model.Account;
import no.digipost.android.model.Letter;
import no.digipost.android.model.PrimaryAccount;
import no.digipost.android.model.Receipt;
import android.accounts.NetworkErrorException;
import android.content.Context;


public class LetterOperations {
	public static final int INBOX = 0;
	public static final int ARCHIVE = 1;
	public static final int WORKAREA = 2;
	public static final int RECEIPTS = 3;

	private final ApiAccess apiAccess;

	public LetterOperations(final Context context) {
		apiAccess = new ApiAccess(context);
	}

	public ArrayList<Letter> getAccountContentMeta(final String access_token, final int type) throws NetworkErrorException {
		Account account = apiAccess.getPrimaryAccount(access_token);
		PrimaryAccount primaryaccount = account.getPrimaryAccount();

		switch (type) {
		case INBOX:
			return apiAccess.getDocuments(access_token, primaryaccount.getInboxUri()).getDocument();
		case ARCHIVE:
			return apiAccess.getDocuments(access_token, primaryaccount.getArchiveUri()).getDocument();
		case WORKAREA:
			return apiAccess.getDocuments(access_token, primaryaccount.getWorkareaUri()).getDocument();
		//case RECEIPTS:
			//return apiAccess.getReceipts(access_token, primaryaccount.getReceiptsUri()).getReceipt();
		default:
			return null;
		}
	}

	public ArrayList<Receipt> getAccountContentMetaReceipt(final String access_token) throws NetworkErrorException {
		Account account = apiAccess.getPrimaryAccount(access_token);
		PrimaryAccount primaryaccount = account.getPrimaryAccount();

		return apiAccess.getReceipts(access_token, primaryaccount.getReceiptsUri()).getReceipt();
	}

	/*
	 * public boolean moveDocument(final String access_token, final Letter
	 * letter) { Letter movedletter = apiAccess.getMovedDocument(access_token,
	 * letter.getUpdateUri(), JSONConverter.createJson(letter)); if (movedletter
	 * == null) { System.out.println("flyttet brev er null"); return false; } if
	 * (movedletter.getLocation().equals(ApiConstants.LOCATION_ARCHIVE)) {
	 * return true; } else { return false; } }
	 */

	public byte[] getDocumentContentPDF(final String access_token, final Letter letter) throws NetworkErrorException {
		ApiAccess.filesize = Integer.parseInt(letter.getFileSize());
		return apiAccess.getDocumentContent(access_token, letter.getContentUri());
	}

	public String getDocumentContentHTML(final String access_token, final Letter letter) throws NetworkErrorException {
		return apiAccess.getDocumentHTML(access_token, letter.getContentUri());
	}

	public byte[] getReceiptContentPDF(final String access_token, final Receipt receipt) throws NetworkErrorException {
		return apiAccess.getDocumentContent(access_token, receipt.getContentAsPDFUri());
	}
}
