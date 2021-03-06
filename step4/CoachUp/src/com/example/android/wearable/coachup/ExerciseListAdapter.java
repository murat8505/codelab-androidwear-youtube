package com.example.android.wearable.coachup;

import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.json.JSONObject;

import com.google.android.gms.location.Geofence;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * ListAdapter to retrieve the list of exercises from the resources and to show
 * it in the phone
 */
public class ExerciseListAdapter implements ListAdapter {
	private String TAG = "ExerciseListAdapter";
	
	/*
     * Use to set an expiration time for a geofence. After this amount
     * of time Location Services will stop tracking the geofence.
     * Remember to unregister a geofence when you're finished with it.
     * Otherwise, your app will use up battery. To continue monitoring
     * a geofence indefinitely, set the expiration time to
     * Geofence#NEVER_EXPIRE.
     */
    private static final long GEOFENCE_EXPIRATION_IN_HOURS = 12;
    private static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS =
            GEOFENCE_EXPIRATION_IN_HOURS * DateUtils.HOUR_IN_MILLIS;
    
    private static final float GEOFENCE_RADIUS = 50.0f;

	private class Item {
		String title;
		String name;
		String summary;
		Bitmap image;
	}

	private List<Item> mItems = new ArrayList<Item>();
	private Context mContext;
	private DataSetObserver mObserver;
	
	// Add geofences handler
    private GeofenceRequester mGeofenceRequester;
    
    // Store a list of geofences to add
    List<Geofence> mCurrentGeofences;
	
	public ExerciseListAdapter(Context context) {
		mContext = context;
		
		// Instantiate a Geofence requester
        mGeofenceRequester = new GeofenceRequester((Activity)mContext);
        
        mCurrentGeofences = new ArrayList<Geofence>();
        
		// call AsynTask to perform network operation on separate thread
        new LoadExerciseAsyncTask().execute(Constants.PLAYLIST_ITEMS_URL,Constants.VIDEOS_URL);
	}

	public void loadExercises(List<Exercise> exerciseList) {
		List<Item> items = parseExercises(exerciseList);
		appendItemsToList(items);
	}
	
	private List<Item> parseExercises(List<Exercise> exerciseList) {
		List<Item> result = new ArrayList<Item>();
		try {
			for (Exercise exercise : exerciseList) {
				Item parsed = new Item();
				parsed.name = exercise.videoId;
				parsed.title = exercise.titleText;
				parsed.image = exercise.bmp;
				parsed.summary = exercise.summaryText;
				result.add(parsed);
				
				Geofence geofence = new Geofence.Builder()
                .setRequestId(exercise.videoId)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setCircularRegion(
                		exercise.latitude,
                		exercise.longitude,
                		GEOFENCE_RADIUS)
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .build();
				
				mCurrentGeofences.add(geofence);
			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to parse exercise list: " + e);
		}
		return result;
	}

	private void appendItemsToList(List<Item> items) {
		mItems.addAll(items);
		if (mObserver != null) {
			mObserver.onChanged();
		}
		
		mGeofenceRequester.addGeofences(mCurrentGeofences);
	}

	@Override
	public int getCount() {
		return mItems.size();
	}

	@Override
	public Object getItem(int position) {
		return mItems.get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public int getItemViewType(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView;
		if (view == null) {
			LayoutInflater inf = LayoutInflater.from(mContext);
			view = inf.inflate(R.layout.list_item, null);
		}
		Item item = (Item) getItem(position);
		TextView titleView = (TextView) view.findViewById(R.id.textTitle);
		TextView summaryView = (TextView) view.findViewById(R.id.textSummary);
		ImageView iv = (ImageView) view.findViewById(R.id.imageView);

		titleView.setText(item.title);
		summaryView.setText(item.summary);
		
		if (item.image != null) {
			iv.setImageBitmap(item.image);
		} else {
			iv.setImageDrawable(mContext.getResources().getDrawable(
					R.drawable.ic_noimage));
		}
		return view;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEmpty() {
		return mItems.isEmpty();
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		mObserver = observer;
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		mObserver = null;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return true;
	}

	@Override
	public boolean isEnabled(int position) {
		return true;
	}

	public String getItemName(int position) {
		return mItems.get(position).name;
	}

	public boolean isConnected() {
		ConnectivityManager connMgr = (ConnectivityManager) mContext
				.getSystemService(Activity.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected())
			return true;
		else
			return false;
	}
	
	

	private class LoadExerciseAsyncTask extends AsyncTask<String, Void, List<Exercise>> {
		@Override
		protected List<Exercise> doInBackground(String... urls) {
			List<Exercise> exerciseList = null;
			try{
				JSONObject playlistItems = AssetUtils.callService(urls[0]);
				//Get video ids from playlistitems json
				String videoIds = AssetUtils.getVideoIdsFromPlaylistItemsJson(playlistItems);
				//Retrieve videos json from YouTube service
				JSONObject videos = AssetUtils.callService(urls[1] + videoIds);
				//Get exercises from videos json
				exerciseList = AssetUtils.getExerciseListFromVideosJson(videos);
				for (Exercise exercise : exerciseList){
					URL url = new URL(exercise.exerciseImage);
					Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
					exercise.bmp = bmp;
				}
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to load exercise list: " + e);
			}
			return exerciseList;
		}

		// onPostExecute displays the results of the AsyncTask.
		@Override
		protected void onPostExecute(List<Exercise> exerciseList) {
			ExerciseListAdapter.this.loadExercises(exerciseList);
		}
	}
}
