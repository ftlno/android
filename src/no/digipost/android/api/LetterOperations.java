package no.digipost.android.api;

import java.util.ArrayList;

import no.digipost.android.model.Account;
import no.digipost.android.model.Documents;
import no.digipost.android.model.Letter;
import no.digipost.android.model.PrimaryAccount;

public class LetterOperations {
	private final ApiAccess apiAcess;

	public LetterOperations() {
		apiAcess = new ApiAccess();
	}

	public ArrayList<Letter> getLetterList(final String access_token) {
		Account account = apiAcess.getPrimaryAccount(access_token);
		PrimaryAccount primaryAccount = account.getPrimaryAccount();
		Documents documents = apiAcess.getDokuments(access_token, primaryAccount.getDokumentsUri());

		return documents.getDokument();
	}
}
