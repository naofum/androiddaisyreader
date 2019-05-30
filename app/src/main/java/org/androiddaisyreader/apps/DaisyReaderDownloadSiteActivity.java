package org.androiddaisyreader.apps;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.androiddaisyreader.adapter.WebsiteAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.metadata.MetaDataHandler;
import org.androiddaisyreader.model.Website;
import org.androiddaisyreader.utils.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.github.naofum.androiddaisyreader.R;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The Class DaisyReaderDownloadSiteActivity.
 */
public class DaisyReaderDownloadSiteActivity extends DaisyEbookReaderBaseActivity {

    private ListView mListViewWebsite;
    private List<Website> listWebsite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_download_site);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.download_sites);

        mListViewWebsite = (ListView) findViewById(R.id.list_view_website);
        initListWebsite();
        WebsiteAdapter websiteAdapter = new WebsiteAdapter(listWebsite, getLayoutInflater());
        mListViewWebsite.setAdapter(websiteAdapter);

        // set listener while touch on website
        mListViewWebsite.setOnItemClickListener(onItemWebsiteClick);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case android.R.id.home:
            backToTopScreen();
            break;

        default:
            return super.onOptionsItemSelected(item);
        }
        return false;
    }

    private OnItemClickListener onItemWebsiteClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            Website website = listWebsite.get(arg2);
            boolean isDoubleTap = handleClickItem(arg2);
            if (isDoubleTap) {
                String websiteUrl = website.getSiteURL();
                String websiteName = website.getSiteName();
                pushToWebsite(websiteUrl, websiteName);
            } else {
                speakTextOnHandler(website.getSiteName());
            }
        }
    };

    /**
     * Inits the list website.
     */
    private void initListWebsite() {
        listWebsite = new ArrayList<Website>();
        Website website = null;
//        website = new Website(this.getString(R.string.web_site_name_daisy_org),
//                this.getString(R.string.web_site_url_daisy_org), 1);
//        listWebsite.add(website);
//        website = new Website(this.getString(R.string.web_site_name_htctu),
//                this.getString(R.string.web_site_url_htctu), 2);
//        listWebsite.add(website);
//        website = new Website(this.getString(R.string.web_site_name_daisy_factory),
//                this.getString(R.string.web_site_url_daisy_factory), 3);
//        listWebsite.add(website);

        NodeList nList = null;
        InputStream databaseInputStream = null;
        try {
            databaseInputStream = new FileInputStream(Constants.folderContainMetadata
                    + Constants.META_DATA_FILE_NAME);
            MetaDataHandler metadata = new MetaDataHandler();

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder;
            dBuilder = dbFactory.newDocumentBuilder();
            Document doc;
            doc = dBuilder.parse(databaseInputStream);
            doc.getDocumentElement().normalize();
            nList = doc.getElementsByTagName(Constants.ATT_WEBSITE);
            for (int i = 0; i < nList.getLength(); i++) {
                Element e = (Element) nList.item(i);
                String urlWebsite = e.getAttribute(Constants.ATT_URL);
                String websiteName = e.getAttribute(Constants.ATT_NAME);
//                String websiteName = getString(getResources().getIdentifier(urlWebsite, "string", getPackageName()));
                website = new Website(websiteName, urlWebsite, i + 1);
                listWebsite.add(website);
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderDownloadSiteActivity.this);
            ex.writeLogException();
        } finally {
            try {
                if (databaseInputStream != null) {
                    databaseInputStream.close();
                }
            } catch (IOException e) {
                //
            }
        }
    }

    /**
     * Push to list book of website.
     */
    private void pushToWebsite(String websiteURL, String websiteName) {
        Intent intent = new Intent(this, DaisyReaderDownloadBooks.class);
        intent.putExtra(Constants.LINK_WEBSITE, websiteURL);
        intent.putExtra(Constants.NAME_WEBSITE, websiteName);
        this.startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        speakText(getString(R.string.download_sites));

    }

    @Override
    protected void onDestroy() {
        try {
            if (mTts != null) {
                mTts.shutdown();
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderDownloadSiteActivity.this);
            ex.writeLogException();
        }
        super.onDestroy();
    }

}
