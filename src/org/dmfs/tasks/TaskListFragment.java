/*
 * Copyright (C) 2012 Marten Gajda <marten@dmfs.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.dmfs.tasks;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

import org.dmfs.provider.tasks.TaskContract.Instances;
import org.dmfs.provider.tasks.TaskContract.Tasks;
import org.dmfs.tasks.model.adapters.TimeFieldAdapter;
import org.dmfs.tasks.utils.ExpandableChildDescriptor;
import org.dmfs.tasks.utils.ExpandableGroupDescriptor;
import org.dmfs.tasks.utils.ExpandableGroupDescriptorAdapter;
import org.dmfs.tasks.utils.OnChildLoadedListener;
import org.dmfs.tasks.utils.TimeRangeCursorFactory;
import org.dmfs.tasks.utils.TimeRangeCursorLoaderFactory;
import org.dmfs.tasks.utils.ViewDescriptor;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupCollapseListener;
import android.widget.ListView;
import android.widget.TextView;


/**
 * A list fragment representing a list of Tasks. This fragment also supports tablet devices by allowing list items to be given an 'activated' state upon
 * selection. This helps indicate which item is currently being viewed in a {@link TaskViewDetailFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks} interface.
 */
public class TaskListFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>, OnChildLoadedListener
{

	private static final String TAG = "org.dmfs.tasks.TaskListFragment";

	private static final String PARAM_EXPANDED_GROUPS = "expanded_groups";

	/**
	 * The projection we use when we load instances. We don't need every detail of a task here.
	 */
	private final static String[] INSTANCE_PROJECTION = new String[] { Instances.INSTANCE_START, Instances.INSTANCE_DURATION, Instances.INSTANCE_DUE,
		Instances.IS_ALLDAY, Instances.TZ, Instances.TITLE, Instances.LIST_COLOR, Instances.PRIORITY, Instances.LIST_ID, Instances.TASK_ID, Instances._ID };

	/**
	 * An adapter to load the due date from the instances projection.
	 */
	private final static TimeFieldAdapter DUE_ADAPTER = new TimeFieldAdapter(Instances.INSTANCE_DUE, Instances.TZ, Instances.IS_ALLDAY);

	private static final String STATE_ACTIVATED_POSITION_GROUP = "activated_group_position";
	private static final String STATE_ACTIVATED_POSITION_CHILD = "activated_child_position";

	/**
	 * A {@link ViewDescriptor} that knows how to present the tasks in the task list.
	 */
	private final ViewDescriptor TASK_VIEW_DESCRIPTOR = new ViewDescriptor()
	{
		/**
		 * We use this to get the current time.
		 */
		private Time mNow;

		/**
		 * The formatter we use for due dates other than today.
		 */
		private final DateFormat mDateFormatter = DateFormat.getDateInstance(SimpleDateFormat.MEDIUM);

		/**
		 * The formatter we use for tasks that are due today.
		 */
		private final DateFormat mTimeFormatter = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT);


		@Override
		public void populateView(View view, Cursor cursor)
		{
			TextView title = (TextView) view.findViewById(android.R.id.title);
			if (title != null)
			{
				String text = cursor.getString(5);
				title.setText(text);
			}
			TextView dueDateField = (TextView) view.findViewById(R.id.task_due_date);
			if (dueDateField != null)
			{
				Time dueDate = DUE_ADAPTER.get(cursor);

				if (dueDate != null)
				{
					if (mNow == null)
					{
						mNow = new Time();
					}
					mNow.clear(TimeZone.getDefault().getID());
					mNow.setToNow();

					dueDateField.setText(makeDueDate(dueDate));

					// highlight overdue dates & times
					if (dueDate.before(mNow))
					{
						dueDateField.setTextColor(Color.RED);
					}
					else
					{
						dueDateField.setTextColor(Color.argb(255, 0x80, 0x80, 0x80));
					}
				}
				else
				{
					dueDateField.setText("");
				}
			}

			View colorbar = view.findViewById(R.id.colorbar);
			if (colorbar != null)
			{
				colorbar.setBackgroundColor(cursor.getInt(6));
			}
		}


		@Override
		public int getView()
		{
			return R.layout.task_list_element;
		}


		/**
		 * Get the due date to show. It returns just a time for tasks that are due today and a date otherwise.
		 * 
		 * @param due
		 *            The due date to format.
		 * @return A String with the formatted date.
		 */
		private String makeDueDate(Time due)
		{
			due.switchTimezone(TimeZone.getDefault().getID());
			if (due.year == mNow.year && due.yearDay == mNow.yearDay)
			{
				return mTimeFormatter.format(new Date(due.toMillis(false)));
			}
			else
			{
				return mDateFormatter.format(new Date(due.toMillis(false)));
			}
		}
	};

	/**
	 * A {@link ViewDescriptor} that knows how to present due date groups.
	 */
	private final ViewDescriptor DUE_GROUP_VIEW_DESCRIPTOR = new ViewDescriptor()
	{
		private final String[] mMonthNames = DateFormatSymbols.getInstance().getMonths();


		@Override
		public void populateView(View view, Cursor cursor)
		{
			TextView title = (TextView) view.findViewById(android.R.id.title);
			if (title != null)
			{
				title.setText(getTitle(cursor) + " (" + mAdapter.getChildrenCount(cursor.getPosition()) + ")");
			}
		}


		@Override
		public int getView()
		{
			return R.layout.task_list_group;
		}


		/**
		 * Return the title of a due date group.
		 * 
		 * @param cursor
		 *            A {@link Cursor} pointing to the current group.
		 * @return A {@link String} with the group name.
		 */
		private String getTitle(Cursor cursor)
		{
			int type = cursor.getInt(cursor.getColumnIndex(TimeRangeCursorFactory.RANGE_TYPE));
			if (type == 0)
			{
				return appContext.getString(R.string.task_group_no_due);
			}
			if ((type & TimeRangeCursorFactory.TYPE_END_OF_TODAY) == TimeRangeCursorFactory.TYPE_END_OF_TODAY)
			{
				return appContext.getString(R.string.task_group_due_today);
			}
			if ((type & TimeRangeCursorFactory.TYPE_END_OF_YESTERDAY) == TimeRangeCursorFactory.TYPE_END_OF_YESTERDAY)
			{
				return appContext.getString(R.string.task_group_overdue);
			}
			if ((type & TimeRangeCursorFactory.TYPE_END_OF_TOMORROW) == TimeRangeCursorFactory.TYPE_END_OF_TOMORROW)
			{
				return appContext.getString(R.string.task_group_due_tomorrow);
			}
			if ((type & TimeRangeCursorFactory.TYPE_END_IN_7_DAYS) == TimeRangeCursorFactory.TYPE_END_IN_7_DAYS)
			{
				return appContext.getString(R.string.task_group_due_within_7_days);
			}
			if ((type & TimeRangeCursorFactory.TYPE_END_OF_A_MONTH) != 0)
			{
				return appContext.getString(R.string.task_group_due_in_month,
					mMonthNames[cursor.getInt(cursor.getColumnIndex(TimeRangeCursorFactory.RANGE_MONTH))]);
			}
			if ((type & TimeRangeCursorFactory.TYPE_END_OF_A_YEAR) != 0)
			{
				return appContext.getString(R.string.task_group_due_in_year, cursor.getInt(cursor.getColumnIndex(TimeRangeCursorFactory.RANGE_YEAR)));
			}
			if ((type & TimeRangeCursorFactory.TYPE_NO_END) != 0)
			{
				return appContext.getString(R.string.task_group_due_in_future);
			}
			return "";
		}

	};

	/**
	 * A descriptor that knows how to load elements in a due date group.
	 */
	private final ExpandableChildDescriptor DUE_DATE_CHILD_DESCRIPTOR = new ExpandableChildDescriptor(Instances.CONTENT_URI, INSTANCE_PROJECTION,
		Instances.VISIBLE + "=1 and (((" + Instances.INSTANCE_DUE + ">=?) and (" + Instances.INSTANCE_DUE + "<?)) or " + Instances.INSTANCE_DUE + " is ?)",
		Instances.DEFAULT_SORT_ORDER, 0, 1, 0).setViewDescriptor(TASK_VIEW_DESCRIPTOR);

	/**
	 * A descriptor for the "grouped by due date" view.
	 */
	private final ExpandableGroupDescriptor GROUP_BY_DUE_DESCRIPTOR = new ExpandableGroupDescriptor(new TimeRangeCursorLoaderFactory(
		TimeRangeCursorFactory.DEFAULT_PROJECTION), DUE_DATE_CHILD_DESCRIPTOR).setViewDescriptor(DUE_GROUP_VIEW_DESCRIPTOR);

	/**
	 * The fragment's current callback object, which is notified of list item clicks.
	 */
	private Callbacks mCallbacks;
	private int mActivatedPositionGroup = ExpandableListView.INVALID_POSITION;
	private int mActivatedPositionChild = ExpandableListView.INVALID_POSITION;
	long[] expandedIds = new long[0];
	private ExpandableListView expandLV;
	private Context appContext;
	private ExpandableGroupDescriptorAdapter mAdapter;
	private Handler mHandler;

	private long[] mSavedExpandedGroups = null;

	// private static final TimeFieldAdapter TFADAPTER = new TimeFieldAdapter(TaskContract.Tasks.DUE, TaskContract.Tasks.TZ, TaskContract.Tasks.IS_ALLDAY);

	// private TaskItemGroup[] itemGroupArray;

	/**
	 * A callback interface that all activities containing this fragment must implement. This mechanism allows activities to be notified of item selections.
	 */
	public interface Callbacks
	{
		/**
		 * Callback for when an item has been selected.
		 */
		public void onItemSelected(Uri taskUri);


		public void onAddNewTask();
	}


	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes).
	 */
	public TaskListFragment()
	{
	}


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mHandler = new Handler();
		setHasOptionsMenu(true);
	}


	@Override
	public void onViewCreated(View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		/*
		 * // Restore the previously serialized activated item position. if (savedInstanceState != null &&
		 * savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) { setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION)); }
		 */
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View rootView = inflater.inflate(R.layout.fragment_expandable_task_list, container, false);
		expandLV = (ExpandableListView) rootView.findViewById(android.R.id.list);
		mAdapter = new ExpandableGroupDescriptorAdapter(appContext, getLoaderManager(), GROUP_BY_DUE_DESCRIPTOR);
		expandLV.setAdapter(mAdapter);
		expandLV.setOnChildClickListener(mTaskItemClickListener);
		expandLV.setOnGroupCollapseListener(mTaskListCollapseListener);
		mAdapter.setOnChildLoadedListener(this);

		getLoaderManager().restartLoader(0, null, this);

		if (savedInstanceState != null)
		{
			Log.d(TAG, "savedInstance state is not null");
			// store expanded groups array for later, when the groups have been loaded
			mSavedExpandedGroups = savedInstanceState.getLongArray(PARAM_EXPANDED_GROUPS);
			mActivatedPositionGroup = savedInstanceState.getInt(STATE_ACTIVATED_POSITION_GROUP);
			mActivatedPositionChild = savedInstanceState.getInt(STATE_ACTIVATED_POSITION_CHILD);
		}
		else
		{
			Log.d(TAG, "savedInstancestate is null!!");
		}
		return rootView;
	}


	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		appContext = activity.getApplicationContext();

		// Activities containing this fragment must implement its callbacks.
		if (!(activity instanceof Callbacks))
		{
			throw new IllegalStateException("Activity must implement fragment's callbacks.");
		}

		mCallbacks = (Callbacks) activity;
	}


	@Override
	public void onDetach()
	{
		super.onDetach();

	}

	private final OnChildClickListener mTaskItemClickListener = new OnChildClickListener()
	{

		@Override
		public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id)
		{
			selectChildView(parent, groupPosition, childPosition);
			mActivatedPositionGroup = groupPosition;
			mActivatedPositionChild = childPosition;
			return true;
		}

	};

	private final OnGroupCollapseListener mTaskListCollapseListener = new OnGroupCollapseListener()
	{

		@Override
		public void onGroupCollapse(int groupPosition)
		{
			if (groupPosition == mActivatedPositionGroup)
			{
				mActivatedPositionChild = ExpandableListView.INVALID_POSITION;
				mActivatedPositionGroup = ExpandableListView.INVALID_POSITION;
			}

		}
	};


	private void selectChildView(ExpandableListView expandLV, int groupPosition, int childPosition)
	{
		// a task instance element has been clicked, get it's instance id and notify the activity
		ExpandableListAdapter listAdapter = expandLV.getExpandableListAdapter();
		Cursor cursor = (Cursor) listAdapter.getChild(groupPosition, childPosition);

		if (cursor == null)
		{
			return;
		}
		// TODO: for now we get the id of the task, not the instance, once we support recurrence we'll have to change that
		Long selectTaskId = cursor.getLong(cursor.getColumnIndex(Instances.TASK_ID));

		if (selectTaskId != null)
		{
			// Notify the active callbacks interface (the activity, if the fragment is attached to one) that an item has been selected.

			// TODO: use the instance URI one we support recurrence
			Uri taskUri = ContentUris.withAppendedId(Tasks.CONTENT_URI, selectTaskId);

			mCallbacks.onItemSelected(taskUri);
		}
	}


	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		Log.d(TAG, "onSaveInstanceState called");
		super.onSaveInstanceState(outState);
		outState.putLongArray(PARAM_EXPANDED_GROUPS, getExpandedGroups());

		if (mActivatedPositionGroup != ExpandableListView.INVALID_POSITION)
		{

			outState.putInt(STATE_ACTIVATED_POSITION_GROUP, mActivatedPositionGroup);

		}
		if (mActivatedPositionChild != ExpandableListView.INVALID_POSITION)
		{
			outState.putInt(STATE_ACTIVATED_POSITION_CHILD, mActivatedPositionChild);

		}

	}


	/**
	 * Turns on activate-on-click mode. When this mode is on, list items will be given the 'activated' state when touched.
	 */
	public void setActivateOnItemClick(boolean activateOnItemClick)
	{
		// When setting CHOICE_MODE_SINGLE, ListView will automatically
		// give items the 'activated' state when touched.
		expandLV.setChoiceMode(activateOnItemClick ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.task_list_fragment_menu, menu);
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.add_task_item:
				mCallbacks.onAddNewTask();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}


	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1)
	{
		return GROUP_BY_DUE_DESCRIPTOR.getGroupCursorLoader(appContext);
	}


	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
	{
		/*
		 * int scrollx = expandLV.getFirstVisiblePosition(); View itemView = expandLV.getChildAt(0); int scrolly = itemView == null ? 0 : itemView.getTop();
		 * Log.v(TAG, "scrollY " + scrollx + "  " + scrolly);
		 */
		Log.v(TAG, "change cursor");
		mAdapter.changeCursor(cursor);
		/*
		 * expandLV.setSelectionFromTop(scrollx, 0); int scrollx2 = expandLV.getFirstVisiblePosition(); View itemView2 = expandLV.getChildAt(0); int scrolly2 =
		 * itemView == null ? 0 : itemView2.getTop(); Log.v(TAG, "scrollY " + scrollx2 + "  " + scrolly2);
		 */
		if (mSavedExpandedGroups != null)
		{
			expandedIds = mSavedExpandedGroups;
			setExpandedGroups();
			mSavedExpandedGroups = null;
		}

	}


	@Override
	public void onLoaderReset(Loader<Cursor> loader)
	{
		mAdapter.changeCursor(null);
	}


	public long[] getExpandedGroups()
	{
		ExpandableListAdapter adapter = expandLV.getExpandableListAdapter();
		int count = adapter.getGroupCount();

		long[] result = new long[count];

		int idx = 0;
		for (int i = 0; i < count; ++i)
		{
			if (expandLV.isGroupExpanded(i))
			{
				result[idx] = adapter.getGroupId(i);
				++idx;
			}
		}
		return Arrays.copyOf(result, idx);
	}


	public void setExpandedGroups()
	{
		ExpandableListAdapter adapter = expandLV.getExpandableListAdapter();
		Arrays.sort(expandedIds);
		Log.d(TAG, "NOW EXPANDING : " + expandLV.getCount());
		int count = adapter.getGroupCount();
		for (int i = 0; i < count; ++i)
		{
			if (Arrays.binarySearch(expandedIds, adapter.getGroupId(i)) >= 0)
			{
				expandLV.expandGroup(i);
			}
		}
	}


	@Override
	public void onChildLoaded(int pos)
	{
		if (mActivatedPositionGroup != ExpandableListView.INVALID_POSITION)
		{
			if (pos == mActivatedPositionGroup && mActivatedPositionChild != ExpandableListView.INVALID_POSITION)
			{
				Log.d(TAG, "Restoring Child Postion : " + mActivatedPositionChild);
				Log.d(TAG, "Restoring Group Position : " + mActivatedPositionGroup);
				mHandler.post(setOpenHandler);

			}
		}

	}


	public int getOpenChildPosition()
	{
		return mActivatedPositionChild;
	}


	public int getOpenGroupPosition()
	{
		return mActivatedPositionGroup;
	}


	public void setOpenChildPosition(int openChildPosition)
	{
		mActivatedPositionChild = openChildPosition;

	}


	public void setOpenGroupPosition(int openGroupPosition)
	{
		mActivatedPositionGroup = openGroupPosition;

	}

	Runnable setOpenHandler = new Runnable()
	{
		@Override
		public void run()
		{
			selectChildView(expandLV, mActivatedPositionGroup, mActivatedPositionChild);
			setExpandedGroups();

		}
	};


	public void setExpandedGroupsIds(long[] ids)
	{
		Log.d(TAG, "SET EXPAND :" + ids);
		expandedIds = ids;

	}

}
