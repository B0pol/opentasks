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

package org.dmfs.tasks.utils;

import java.lang.ref.WeakReference;

import org.dmfs.tasks.model.Model;
import org.dmfs.tasks.model.Sources;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;


/**
 * An asynchronous model loader. Loads a specific model in the background and notifies a listener when the operation is finished.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class AsyncModelLoader extends AsyncTask<String, Void, Model>
{

	private WeakReference<OnModelLoadedListener> mListener;
	private Context mContext;


	/**
	 * Create a new AsyncModelLoader.
	 * 
	 * @param context
	 *            The application context.
	 * @param listener
	 *            The {@link OnModelLoadedListener} that receives the model.
	 */
	public AsyncModelLoader(Context context, OnModelLoadedListener listener)
	{
		mContext = context;
		mListener = new WeakReference<OnModelLoadedListener>(listener);
		Log.v("äääääääääää", " ~~~~~~~~~~~~~~~~~~~~~~~~~created " + this);
	}


	@Override
	protected final Model doInBackground(String... accountTypes)
	{
		final OnModelLoadedListener target = mListener.get();

		if (target != null)
		{

			Sources sources = Sources.getInstance(mContext);
			Model rModel;
			rModel = sources.getModel(accountTypes[0]);
			if (rModel == null)
			{
				rModel = sources.getDefaultModel();
			}
			return rModel;
		}
		else
		{
			return null;
		}
	}

	@Override
	protected void finalize()
	{
		try
		{
			super.finalize();
		}
		catch (Throwable e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.v("äääääääääää", " ~~~~~~~~~~~~~~~~~~~~~~~~~finalzed " + this);
	}



	@Override
	protected final void onPostExecute(Model model)
	{
		final OnModelLoadedListener target = mListener.get();
		if (target != null)
		{
			target.onModelLoaded(model);
		}
	}
}
