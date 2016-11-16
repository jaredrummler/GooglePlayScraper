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

package com.jrummyapps.googleplayscraper;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web scrape app details pages on Google Play's website.
 */
public class GooglePlayScraper {

  /**
   * Web scrape app data from https://play.google.com
   *
   * @param packageName The app's package name
   * @return The app details scraped from the Play Store.
   * @throws WebScrapeException if an error occurred while scraping Google Play.
   */
  public static AppDetails scrapeAppDetails(String packageName) throws WebScrapeException {
    try {
      Document document = Jsoup.connect("https://play.google.com/store/apps/details?id=" + packageName).get();
      NumberFormat numberFormatter = NumberFormat.getNumberInstance(Locale.US);

      AppDetails details = new AppDetails(packageName);

      Elements detailsInfo = document.select("div.details-info");
      Elements additionalInfo = document.select(".details-section-contents");
      Elements categoryInfo = detailsInfo.select(".category");

      details.title = detailsInfo.select("[class=document-title]").first().text().trim();
      details.icon = detailsInfo.select("img.cover-image").attr("abs:src");
      details.developer = detailsInfo.select("span[itemprop=\"name\"]").first().text().trim();
      details.summary = document.select("meta[name=\"description\"]").first().attr("content");
      details.description = additionalInfo.select("div[itemprop=description] div").html();
      details.category = categoryInfo.text();
      details.genre = categoryInfo.attr("href").substring(categoryInfo.attr("href").lastIndexOf("/") + 1);
      details.offersInAppPurchases = !detailsInfo.select(".inapp-msg").isEmpty();
      details.containsAds = !detailsInfo.select(".ads-supported-label-msg").isEmpty();
      details.version = additionalInfo.select("div.content[itemprop=\"softwareVersion\"]").text().trim();
      details.datePublished = additionalInfo.select("div.content[itemprop=\"datePublished\"]").text().trim();
      details.contentRating = additionalInfo.select("div.content[itemprop=\"contentRating\"]").text().trim();
      details.fileSize = additionalInfo.select("div.content[itemprop=\"fileSize\"]").text().trim();
      details.developerEmail = additionalInfo.select(".dev-link[href^=\"mailto:\"]").attr("href").split(":")[1];
      details.rating = Float.parseFloat(document.select(".rating-box div.score").text());
      details.video = document.select(".screenshots span.preview-overlay-container[data-video-url]")
          .attr("data-video-url").split("\\?")[0];
      details.reviews = parseAppReviews(document);

      // Price
      String price = detailsInfo.select("meta[itemprop=price]").attr("content");
      if (price.equals("0")) {
        details.price = 0;
      } else {
        details.price = NumberFormat.getCurrencyInstance(Locale.US).parse(price).doubleValue();
      }

      // Required Android Version
      String operatingSystems = additionalInfo.select("div.content[itemprop=\"operatingSystems\"]").text().trim();
      Pattern versionPattern = Pattern.compile("^([\\d\\.]+)");
      Matcher minVersionMatcher = versionPattern.matcher(operatingSystems);
      if (minVersionMatcher.find()) {
        details.minOsVersion = minVersionMatcher.group(1);
      }

      // Download Count
      String downloads = additionalInfo.select("div.content[itemprop=\"numDownloads\"]").text();
      for (String regex : new String[]{" - ", " et ", "-", "～", " a "}) {
        String[] installs = downloads.split(regex);
        if (installs.length == 2) {
          try {
            details.minDownloads = numberFormatter.parse(installs[0]).intValue();
            details.maxDownloads = numberFormatter.parse(installs[1]).intValue();
            break;
          } catch (ParseException ignored) {
          }
        }
      }

      // Developer Website
      String devLink = additionalInfo.select(".dev-link[href^=\"http\"]").attr("href");
      try {
        String query = new URL(devLink).getQuery();
        details.developerWebsite = query.substring(query.indexOf("http"), query.indexOf("&"));
      } catch (MalformedURLException | IndexOutOfBoundsException ignored) {
        details.developerWebsite = devLink;
      }

      // Ratings Histogram
      Element ratings = document.select(".rating-histogram").first();
      details.ratingHistogram = new int[]{
          numberFormatter.parse(ratings.select(".one .bar-number").text()).intValue(),
          numberFormatter.parse(ratings.select(".two .bar-number").text()).intValue(),
          numberFormatter.parse(ratings.select(".three .bar-number").text()).intValue(),
          numberFormatter.parse(ratings.select(".four .bar-number").text()).intValue(),
          numberFormatter.parse(ratings.select(".five .bar-number").text()).intValue(),
      };

      // Screenshots
      Elements screenshots = document.select(".thumbnails .screenshot");
      int numScreenshots = screenshots.size();
      details.screenshots = new String[numScreenshots];
      for (int i = 0; i < numScreenshots; i++) {
        details.screenshots[i] = screenshots.get(i).attr("abs:src");
      }

      // What's New
      Elements recentChanges = document.select(".recent-change");
      StringBuilder whatsNew = new StringBuilder();
      String newLine = "";
      for (Element recentChange : recentChanges) {
        whatsNew.append(newLine).append(recentChange.text());
        newLine = "\n";
      }
      details.whatsNew = whatsNew.toString();

      return details;
    } catch (Exception e) {
      if (e instanceof HttpStatusException) {
        throw new WebScrapeException("HTTP response was not OK. Are you sure " + packageName + " is on Google Play?");
      }
      throw new WebScrapeException("Error parsing response for " + packageName, e);
    }
  }

  private static List<AppReview> parseAppReviews(Document document) {
    Elements select = document.select("div[class*=single-review]");
    List<AppReview> appReviews = new ArrayList<>();

    for (Element element : select) {
      AppReview review = new AppReview();
      review.timestamp = new Date();

      Elements innerSelect = element.select("a[href*=people]");
      review.authorUrl = hasNoSelection(innerSelect) ? "" : "https://play.google.com" + innerSelect.get(0).attr("href");

      innerSelect = element.select("span[class*=author-name] > a");
      review.authorName = hasNoSelection(innerSelect) ? "" : innerSelect.get(0).text();

      innerSelect = element.select("a[href*=people] > img");
      review.authorPicUrl = hasNoSelection(innerSelect) ? "" : innerSelect.get(0).attr("src").trim();

      innerSelect = element.select("a[class*=permalink]");
      review.permalink = hasNoSelection(innerSelect) ? "" : "https://play.google.com" + innerSelect.get(0).attr("href");

      innerSelect = element.select("span[class*=review-date]");
      if (hasSelection(innerSelect)) {
        review.reviewDate = innerSelect.get(0).text().trim();
      }

      innerSelect = element.select("div[class*=current-rating]");
      review.starRatings = hasNoSelection(innerSelect) ? -1 : Integer.parseInt(innerSelect.get(0).attr("style").
          replace("width:", "").replace("%;", "").trim()) / 20;

      innerSelect = element.select("div[class*=review-body] > span");
      review.reviewTitle = hasNoSelection(innerSelect) ? "" : innerSelect.get(0).text().trim();

      innerSelect = element.select("div[class*=review-body]");
      String expandReviewText = innerSelect.select("a").text();
      review.reviewBody = hasNoSelection(innerSelect) ? "" : innerSelect.get(0).text()
          .replace(review.reviewTitle, "").replace(expandReviewText, "")
          .trim();

      appReviews.add(review);
    }

    return appReviews;
  }

  private static boolean hasNoSelection(Elements select) {
    return (select == null || select.size() == 0);
  }

  private static boolean hasSelection(Elements select) {
    return !hasNoSelection(select);
  }

  public static class AppReview {

    public String authorUrl;
    public String authorName;
    public String authorPicUrl;
    public String reviewDate;
    public String permalink;
    public int starRatings;
    public String reviewTitle;
    public String reviewBody;
    public Date timestamp;
  }

  public static class AppDetails {

    public final String packageName;
    public String title;
    public String icon;
    public String developer;
    public String developerEmail;
    public String developerWebsite;
    public String category;
    public String genre;
    public String summary;
    public String description;
    public String version;
    public String datePublished;
    public String minOsVersion;
    public String contentRating;
    public String fileSize;
    public double price;
    public boolean offersInAppPurchases;
    public boolean containsAds;
    public int minDownloads;
    public int maxDownloads;
    public int[] ratingHistogram;
    public float rating;
    public String video;
    public String[] screenshots;
    public List<AppReview> reviews;
    public String whatsNew;

    public AppDetails(String packageName) {
      this.packageName = packageName;
    }

  }

  public static class WebScrapeException extends Exception {

    public WebScrapeException(String message) {
      super(message);
    }

    public WebScrapeException(String message, Throwable cause) {
      super(message, cause);
    }

  }

}
