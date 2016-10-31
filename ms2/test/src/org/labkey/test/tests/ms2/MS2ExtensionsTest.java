package org.labkey.test.tests.ms2;

import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.categories.DailyA;
import org.labkey.test.categories.MS2;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category({MS2.class, DailyA.class})
public class MS2ExtensionsTest extends AbstractMS2ImportTest
{
    protected static final String MS2EXTENSIONS_WEBPART = "MS2 Runs With Peptide Counts";
    protected static final String MS2EXTENSIONS_MODULE = "MS2Extensions";
    protected static final String MS2_FOLDER_NAME = "ms2folder";
    protected static final String SEARCH_STRING = "gi|11";
    protected static final String MS2EXTENSIONS_DATAREGION_NAME = "MS2ExtensionsRunGrid";
    protected static final String MULTI_PROTEIN_MSG = "Multiple proteins match your search. Please choose the applicable proteins below";
    protected static final String NO_PROTEIN_MSG = "No proteins match. Please try another name.";
    protected static Locator.XPathLocator matchCriteriaComo = Locator.tagWithName("select", "targetProteinMatchCriteria");
    protected static Locator.XPathLocator targetProteinInput = Locator.input("targetProtein");
    protected static Locator.XPathLocator matchedProteinCheckbox = Locator.input("targetSeqIds");

    @Override
    @LogMethod
    protected void setupMS2()
    {
        cleanPipeline();
        super.setupMS2();
        importMS2Run("DRT2", 2);
    }

    @Override
    protected void verifyMS2()
    {
        verifyMultiProteinsSearch();
    }

    private void verifyMultiProteinsSearch()
    {
        _containerHelper.enableModule(MS2EXTENSIONS_MODULE);
        PortalHelper _portalHelper = new PortalHelper(this);
        _portalHelper.addWebPart(MS2EXTENSIONS_WEBPART);

        log("Verify protein search with match criteria of Prefix");
        assertEquals("Default match criteria should be Prefix", "Prefix", getSelectedOptionText(matchCriteriaComo));

        compareWithCriteria("Prefix");

        waitForText(MULTI_PROTEIN_MSG);
        int proteinCounts = matchedProteinCheckbox.findElements(getDriver()).size();
        assertTrue("No matched proteins found for \"" + SEARCH_STRING + "\" with match criteria \"Prefix\"", proteinCounts > 1);
        clickButton("Continue");

        waitForText("Compare Peptides: Protein gi|11");
        DataRegionTable compDetailTable = new DataRegionTable("query", getDriver());
        assertTrue("Comparison Details shouldn't be empty for " + SEARCH_STRING + "\" with match criteria \"Prefix\"", compDetailTable.getDataRowCount() > 0);

        log("Verify protein search with match criteria of Substring");
        compareWithCriteria("Substring");
        int proteinCountsSubString = matchedProteinCheckbox.findElements(getDriver()).size();
        assertTrue("Wrong number of proteins found for \"" + SEARCH_STRING + "\" with match criteria \"Substring\"", proteinCounts == proteinCountsSubString);

        log("Verify protein search with match criteria of Suffix");
        compareWithCriteria("Suffix");
        waitForText(NO_PROTEIN_MSG);
        assertElementNotPresent("No proteins should be found for \"" + SEARCH_STRING + "\" with match criteria \"Suffix\"", matchedProteinCheckbox);
    }

    private void compareWithCriteria(String matchCriteria)
    {
        clickFolder(MS2_FOLDER_NAME);

        targetProteinInput.waitForElement(getDriver(), 1000);
        setFormElement(targetProteinInput, SEARCH_STRING);
        selectOptionByText(matchCriteriaComo, matchCriteria);
        DataRegionTable runsTable = new DataRegionTable(MS2EXTENSIONS_DATAREGION_NAME, getDriver());
        runsTable.checkAll();
        sleep(1000);
        clickButton("Compare Peptides");
    }

    private void cleanPipeline()
    {
        if (PIPELINE_PATH == null)
            return;
        File rootDir = new File(PIPELINE_PATH);
        delete(new File(rootDir, ".labkey/protocols/rollup/Protocol Rollup 1.xml"));
    }

}
