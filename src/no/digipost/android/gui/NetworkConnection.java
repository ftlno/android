package no.digipost.android.gui;

import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutionException;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

public class NetworkConnection {
	private final Context context;

	public NetworkConnection(final Context context) {
		this.context = context;
	}

	public boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null;
	}

	public boolean isOnline() {
		IsOnlineTask task = new IsOnlineTask();
		try {
			return task.execute().get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	private class IsOnlineTask extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected Boolean doInBackground(final Void... params) {
			try {
				URL myUrl = new URL("https://www.digipost.no");
				URLConnection connection = myUrl.openConnection();
				connection.setConnectTimeout(3000);
				connection.connect();
				return true;
			} catch (Exception e) {
				return false;
			}
		}
	}

	public void showNoNetworkDialog() {
		Builder alertBuilder = new AlertDialog.Builder(context);
		final AlertDialog alert = alertBuilder.create();
		alert.setTitle("Nettverksfeil");
		alert.setMessage("Nettverk ikke tilgjengelig");
		alert.setButton(AlertDialog.BUTTON_POSITIVE, "Lukk", new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialog, final int which) {
				alert.dismiss();
			}
		});
		alert.show();
	}
}