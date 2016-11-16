/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jrummyapps.googleplayscraper.demo;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jrummyapps.googleplayscraper.GooglePlayScraper;
import com.squareup.picasso.Picasso;

public class MainActivity extends AppCompatActivity {

  EditText appIdEditText;
  Button queryButton;
  ImageView iconImageView;
  TextView titleText;
  TextView resultText;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    appIdEditText = (EditText) findViewById(R.id.appId);
    queryButton = (Button) findViewById(R.id.queryBtn);
    iconImageView = (ImageView) findViewById(R.id.icon);
    titleText = (TextView) findViewById(R.id.title);
    resultText = (TextView) findViewById(R.id.result_text);

    queryButton.setOnClickListener(new View.OnClickListener() {

      @Override public void onClick(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(appIdEditText.getWindowToken(), 0);
        new ScrapePlayStoreTask().execute(appIdEditText.getText().toString());
      }
    });
  }

  class ScrapePlayStoreTask extends AsyncTask<String, Void, GooglePlayScraper.AppDetails> {

    private ProgressDialog progressDialog;

    @Override protected void onPreExecute() {
      progressDialog = ProgressDialog.show(MainActivity.this, "Please Wait...", "Scraping Website...");
    }

    @Override protected GooglePlayScraper.AppDetails doInBackground(String... params) {
      try {
        return GooglePlayScraper.scrapeAppDetails(params[0]);
      } catch (GooglePlayScraper.WebScrapeException e) {
        e.printStackTrace();
        return null;
      }
    }

    @Override protected void onPostExecute(GooglePlayScraper.AppDetails appDetails) {
      progressDialog.dismiss();

      if (appDetails == null) {
        Toast.makeText(getApplicationContext(), "Error scraping app details", Toast.LENGTH_LONG).show();
        return;
      }

      titleText.setText(appDetails.title);
      Picasso.with(MainActivity.this).load(appDetails.icon).into(iconImageView);
      setResultText(appDetails);
    }

  }

  void setResultText(GooglePlayScraper.AppDetails details) {
    HtmlBuilder html = new HtmlBuilder();
    html.p().b("Category: ").append(details.category).close();
    html.p().b("Developer: ").append(details.developer).close();
    html.p().b("Description: ").close().p(details.description);
    // You can get a lot more info. This is an incomplete demo. Please improve it with a PR.
    resultText.setText(html.toSpan());
  }

}
