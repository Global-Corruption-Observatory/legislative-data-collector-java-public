package com.precognox.ceu.legislative_data_collector.colombia.recordbuilding;

import com.precognox.ceu.legislative_data_collector.colombia.constants.TextType;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.DownloadedFile;
import com.precognox.ceu.legislative_data_collector.entities.TextSource;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.repositories.DownloadedFileRepository;
import com.precognox.ceu.legislative_data_collector.repositories.TextSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.CachedSources;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWrapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.LawHandler.LAW_ID_REGEX;

/**
 * This class collects the bill and amendment text information (text and url), and is a fallback for collecting the law text
 * It returns only the required parts of the texts.
 * It handles the downloading of PDFS and the saving of the webpage text
 */
@Slf4j
public class GazetteWebpageHandler {
    public static final String GAZETTE_INFORMATION_TEXT_KEY = "text";
    public static final String GAZETTE_INFORMATION_URL_KEY = "url";
    private static final String PAGE_SIZE_ELEMENT_XPATH =
            "/html/body/div[2]/div/table/tbody/tr[4]/td/form/fieldset/div/div/div[1]/span[1]";
    private static final String GAZETTE_WEBPAGE = "http://svrpubindc.imprenta.gov.co/senado/";
    private static final String BUTTON_DETAIL_STRING = "btnVerDetalle";
    private static final String BUTTON_DOWNLOAD_STRING = "btnDescargarPdf";
    private static final String BUTTON_PDFLINK_STRING = "verLink";
    private static final String TEXT_SOURCE_IDENTIFIER_FORMATTER = "Gazette: %s, id: %s";

    public static final Pattern START_TEXT_REGEX = Pattern.compile("DECRETA");
    public static final Pattern ARTICLE_START_TEXT_REGEX =
            Pattern.compile("(?:[aA]rt[.]?|art[iIíÍ́]*c*u*l[o0])[\\s ]+(?:I[^I]|1\\D|[lL][.º°]|[uUúÚ́]*nico|[pP]rimero|uno)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BILL_ID_REGEX = Pattern.compile("([\\d/]*)-([\\d/]*)");
    private static final Pattern GAZETTE_ISSUE_REGEX = Pattern.compile("^(\\d+)/(\\d+)$");
    private static final Pattern GAZETTE_CONTENT_BILL_SELECT_REGEX = Pattern.compile("\\D*(\\d+)\\s*de[\\s\\d]*(\\d\\d)");
    private static final Pattern GAZETTE_CONTENT_BILL_FULL_DATE_SELECT_REGEX =
            Pattern.compile("\\D*(\\d+)\\s*del\\s*\\d*[\\w\\s\\d]+(\\d\\d)");
    private static final Pattern GAZETTE_CONTENT_LAW_SELECT_REGEX =
            Pattern.compile("\\D*(\\d+)\\s*del\\s*\\d*[\\w\\s]*de\\s*(\\d+)");
    private static final Pattern PAGE_SIZE_REGEX = Pattern.compile("Registro 1 a 50.*");
    private static final Pattern SEARCHED_NUMBER_REGEX = Pattern.compile("\\D1$");
    private static final Pattern BILL_TEXT_END_REGEX =
            Pattern.compile("E(?:XPOSICI.N\\s*DE\\s*MOTIVOS|xposici.n\\s*de\\s*[Mm]otivos)|CONSULTAR.*?(?:ORIGINAL\\s*IMPRESO|FOI*RMAT[OE]\\s*PDF|ARCHIVO\\s*PDF)", Pattern.MULTILINE);
    private static final Pattern LAW_TEXT_END_REGEX =
            Pattern.compile("Publíquese y cúmplase", Pattern.MULTILINE);
    private static final Pattern AMENDMENT_TEXT_END_REGEX =
            Pattern.compile("CONSULTAR.*?(?:ORIGINAL\\s*IMPRESO|FOI*RMAT[OE]\\s*PDF|ARCHIVO\\s*PDF)", Pattern.MULTILINE);

    private static final int DETAILED_WEBPAGE_CODE = 1;
    private static final int DOWNLOADING_PDF_CODE = 2;
    private static final int WAITING_FOR_PDF_DOWNLOAD_CODE = 3;
    private static final int NO_TEXT_BUTTONS_FOUND_CODE = 0;
    private static final int GAZETTE_NOT_FOUND_CODE = -1;

    private static final Set<String> gazettesUnderCollection = Collections.synchronizedSet(new HashSet<>());
    private static final CachedSources<TextSource> NOT_YET_SAVED_TEXTS = new CachedSources<>();
    private static final CachedSources<DownloadedFile> NOT_YET_SAVED_FILES = new CachedSources<>();
    private static final Map<String, String> TEXT_TYPE_TO_LINK_TEXT_TYPE = Map.of(
            TextType.AMENDMENT_STAGE_1_TEXT.label, TextType.AMENDMENT_STAGE_1_LINK_TEXT.label,
            TextType.AMENDMENT_STAGE_2_TEXT.label, TextType.AMENDMENT_STAGE_2_LINK_TEXT.label,
            TextType.AMENDMENT_STAGE_3_TEXT.label, TextType.AMENDMENT_STAGE_3_LINK_TEXT.label,
            TextType.AMENDMENT_STAGE_4_TEXT.label, TextType.AMENDMENT_STAGE_4_LINK_TEXT.label,
            TextType.AMENDMENT_STAGE_13_JOINED_TEXT.label, TextType.AMENDMENT_STAGE_13_JOINED_LINK_TEXT.label
    );
    private final WebDriverWait wait;
    private final GazetteType type;
    private final String identifier;
    private final String textSourceType;
    private final ReadDatabaseService readService;
    @Getter
    private WebDriverWrapper browser;
    @Getter
    private String url;
    private String gazetteIssue;
    private boolean secondAttempt = false;

    public GazetteWebpageHandler(
            WebDriverWrapper browser,
            ReadDatabaseService readService,
            GazetteType type,
            String identifier,
            String textSourceType) {
        this.browser = browser;
        this.wait = new WebDriverWait(browser.getWebDriver(), Duration.ofSeconds(90));
        this.readService = readService;
        this.type = type;
        this.identifier = identifier;
        this.textSourceType = textSourceType;
    }

    public static void savePagesToDatabase(TextSourceRepository textRepository,
                                           DownloadedFileRepository fileRepository) {
        NOT_YET_SAVED_TEXTS.saveToDatabase(textRepository);
        NOT_YET_SAVED_FILES.saveToDatabase(fileRepository);
    }

    public Map<String, String> getGazetteInformation(String gazetteIssue, WebDriverWrapper browser)
            throws DataCollectionException {
        this.gazetteIssue = gazetteIssue;
        Optional<TextSource> textSource = tryGettingText(textSourceType, buildTextSourceIdentifier());
        if (textSource.isPresent()) {
            this.url = textSource.get().getDownloadUrl();
            String text;
            if (GazetteType.AMENDMENT.equals(type)) {
                // Amendments can have the required start text in their link text, which means the whole text on the
                // webpage is needed. So the link text is needed as well.
                Optional<TextSource> linkTextOpt =
                        tryGettingText(TEXT_TYPE_TO_LINK_TEXT_TYPE.get(textSourceType), buildTextSourceIdentifier());
                // If this remains blank, the amendment text won't be found in it, so the handler will move on to the
                // actual text to find the required part
                String linkText = "";
                if (linkTextOpt.isPresent()) {
                    linkText = linkTextOpt.get().getTextContent();
                }
                text = getRequiredPartOfAmendmentText(textSource.get().getTextContent(), linkText);
            } else {
                text = getRequiredPartOfText(textSource.get().getTextContent());
            }
            return Map.of(
                    GAZETTE_INFORMATION_URL_KEY, this.url,
                    GAZETTE_INFORMATION_TEXT_KEY, text
            );
        }
        return getGazetteInformationFromWebPage(browser);
    }

    private Map<String, String> getGazetteInformationFromWebPage(WebDriverWrapper browser)
            throws DataCollectionException {
        log.debug("Getting information from Gazette: {}", gazetteIssue);
        if (!isAppropriateIdentifier()) {
            log.error("Incorrect identifier {} for {}", identifier, type);
        }
        Matcher gazetteMatcher = GAZETTE_ISSUE_REGEX.matcher(gazetteIssue);
        if (gazetteMatcher.find()) {
            int navigateCode = findIssue(gazetteMatcher.group(1), gazetteMatcher.group(2), browser);
            String text;
            if (navigateCode == DETAILED_WEBPAGE_CODE) { //Details page found
                String linkText;
                try {
                    linkText = findLegislationInIssue();
                } catch (DataCollectionException e) {
                    secondAttempt = true;
                    try {
                        linkText = findLegislationInIssue();
                    } catch (DataCollectionException ex) {
                        throw ex;
                    }
                }
                secondAttempt = false;
                this.url = getLink();
                text = getText(linkText);

            } else if (navigateCode == DOWNLOADING_PDF_CODE) { //PDF downloading
                Optional<DownloadedFile> pdfFile;
                try {
                    pdfFile = readService.readFileThenDelete(browser.getDownloadDir().toPath(), this.url, gazetteIssue);
                } catch (IOException ex) {
                    throw new RuntimeException(String.format("PDF file for %s in %s could not be deleted! Scraping " +
                            "aborted to preserve data consistency! Cause %s", gazetteIssue, browser.getDownloadDir().getName(), ex));
                }
                pdfFile.ifPresent(NOT_YET_SAVED_FILES::add);
                gazettesUnderCollection.remove(gazetteIssue);
                log.debug("PDF for gazette {} downloaded to database", gazetteIssue);
                String pdfText = readService.getPdfTextContent(pdfFile)
                        .orElseThrow(() -> new DataCollectionException("Text could not be read from the pdf"));
                log.info("PDF text extracted from gazette {}", gazetteIssue);
                text = getTextFromPDF(pdfText);
                log.debug("PDF for gazette {} processed", gazetteIssue);

            } else if (navigateCode == WAITING_FOR_PDF_DOWNLOAD_CODE) { //PDF in database already or under collection
                // => (wait and) get from database
                log.info("PDF for gazette {} already collected", gazetteIssue);
                try {
                    String pdfText = getWholePDFText();
                    text = getTextFromPDF(pdfText);
                    log.debug("PDF for gazette {} processed", gazetteIssue);
                } catch (TimeoutException ex) {
                    log.error("Wait for {} PDF timed out", gazetteIssue);
                    throw new DataCollectionException(String.format("Wait for %s PDF timed out", gazetteIssue));
                }

            } else { //No gazette found matching the requirements or no buttons found
                log.error("{} Gazette issue does not exist", gazetteIssue);
                throw new DataCollectionException("No Gazette issue found");
            }
            return Map.of(
                    GAZETTE_INFORMATION_URL_KEY, this.url,
                    GAZETTE_INFORMATION_TEXT_KEY, text
            );
        } else {
            log.error("{} is an invalid Gazette issue format", gazetteIssue);
            throw new DataCollectionException("Wrong Gazette issue number format");
        }
    }

    private boolean isAppropriateIdentifier() throws DataCollectionException {
        if (type.equals(GazetteType.LAW)) {
            return LAW_ID_REGEX.matcher(identifier).find();
        }
        if (type.equals(GazetteType.BILL) || type.equals(GazetteType.AMENDMENT)) {
            return BILL_ID_REGEX.matcher(identifier).find();
        }
        log.warn("Unknown gazette search type {}", type);
        throw new DataCollectionException("Unknown gazette search type");
    }

    private int findIssue(String gazetteNumber, String yearEnd, WebDriverWrapper browser) throws DataCollectionException {
        try {
            browser.get(GAZETTE_WEBPAGE);
            Select pageSelect = new Select(browser.findElement(By.id("formResumen:dataTableResumen_rppDD")));
            pageSelect.selectByVisibleText("50");
            wait.until(ExpectedConditions.textMatches(By.xpath(PAGE_SIZE_ELEMENT_XPATH), PAGE_SIZE_REGEX));
            WebElement gazetteNumberInput =
                    browser.findElement(By.xpath("/html/body/div[2]/div/table/tbody/tr[4]/td/form/fieldset/div/div/div[2]/table/thead/tr/th[1]/input"));
            gazetteNumberInput.sendKeys(gazetteNumber);
            wait.until(ExpectedConditions.textMatches(By.xpath(PAGE_SIZE_ELEMENT_XPATH), SEARCHED_NUMBER_REGEX));

            Optional<WebElement> requiredRow = findGazetteWithRequiredYear(yearEnd);
            //No gazette found matching the requirements
            return requiredRow.map(webElement -> findAvailableFormOfTheGazette(webElement, gazetteNumber, yearEnd))
                    .orElse(GAZETTE_NOT_FOUND_CODE);

        } catch (NoSuchElementException ex) {
            log.error("Error while reloading the Gazette page, element not found {}", ex.getMessage());
            String errorInformation = getNoElementInformation(ex.getMessage());
            throw new DataCollectionException(String.format("No Gazette issue found for [Matching element not found: %s]", errorInformation));
        } catch (TimeoutException timeout) {
            log.error("Webpage with url -{}- did not load in time", browser.getWebDriver().getCurrentUrl());
            throw new DataCollectionException("Page didn't respond while searching for gazette");
        }
    }

    private Optional<WebElement> findGazetteWithRequiredYear(String yearEnd) {
        return browser.findElements(By.cssSelector(".ui-datatable-tablewrapper tbody tr"))
                .stream()
                .filter(row -> row.findElements(By.xpath(".//td")).size() > 3)
                .filter(row -> row.findElement(By.xpath(".//td[3]/label"))
                        .getText()
                        .trim()
                        .endsWith(yearEnd))
                .findFirst();
    }

    private int findAvailableFormOfTheGazette(WebElement row, String gazetteNumber, String yearEnd) {
        Optional<WebElement> detailPageButton = findButton(row, BUTTON_DETAIL_STRING);
        if (detailPageButton.isPresent()) {
            clickElement(detailPageButton.get());
            return DETAILED_WEBPAGE_CODE; //Details page found

        } else { //Try to get PDF
            if (tryGettingFile().isPresent()
                    || gazettesUnderCollection.contains(gazetteIssue)) {
                //PDF in database already or under collection => (wait) and get from database
                return WAITING_FOR_PDF_DOWNLOAD_CODE;
            } else {
                if (gazettesUnderCollection.add(gazetteIssue)) {
                    return startPDFDownload(row, yearEnd);
                } else {
                    //PDF in database already or under collection => (wait) and get from database
                    return WAITING_FOR_PDF_DOWNLOAD_CODE;
                }
            }
        }
    }

    private Optional<WebElement> findButton(WebElement row, String buttonTypeString) {
        if (Objects.isNull(row)) {
            return Optional.empty();
        }
        WebElement buttonsContainer = row.findElement(By.cssSelector(".colIconoAjustable"));
        if (Objects.isNull(buttonsContainer)) {
            return Optional.empty();
        }
        return buttonsContainer.findElements(By.tagName("button"))
                .stream()
                .filter(button -> isButtonType(button.getAttribute("id"), buttonTypeString))
                .findFirst();
    }

    private boolean isButtonType(String buttonId, String typeString) {
        String[] splitString = buttonId.split(":");
        String type = splitString[splitString.length - 1];
        return type.equals(typeString);
    }

    private void clickElement(WebElement element) {
        JavascriptExecutor jsExe = (JavascriptExecutor) browser.getWebDriver();
        jsExe.executeScript("arguments[0].click();", element);
    }

    private Optional<TextSource> tryGettingText(String type, String identifier) {
        Optional<TextSource> databaseText =
                readService.findByTextTypeAndIdentifierAndCountry(type, identifier, Country.COLOMBIA);
        if (databaseText.isPresent()) {
            return databaseText;
        } else {
            return getTextFromNotYetSaved(type, identifier);
        }
    }

    private Optional<TextSource> getTextFromNotYetSaved(String type, String identifier) {
        synchronized (NOT_YET_SAVED_TEXTS.getLock()) {
            return NOT_YET_SAVED_TEXTS.stream()
                    .filter(source -> source.getTextType().equals(type))
                    .filter(source -> source.getTextIdentifier().equals(identifier))
                    .findAny();
        }
    }

    private Optional<DownloadedFile> tryGettingFile() {
        Optional<DownloadedFile> pdfFile = readService.findByFileName(gazetteIssue);
        if (pdfFile.isPresent()) {
            return pdfFile;
        } else {
            synchronized (NOT_YET_SAVED_FILES.getLock()) {
                return NOT_YET_SAVED_FILES.stream()
                        .filter(source -> source.getFilename().equals(gazetteIssue))
                        .findAny();
            }
        }
    }

    private int startPDFDownload(WebElement row, String yearEnd) {
        Optional<WebElement> requiredRow = Optional.ofNullable(row);
        Optional<WebElement> linkButton = findButton(requiredRow.get(), BUTTON_PDFLINK_STRING);
        if (linkButton.isPresent()) { //Getting link
            clickElement(linkButton.get());
            this.url = browser.findElement(By.id("formResumen:linkDescargar")).getText();
            //Page reloads so reacquire the elements
            requiredRow = findGazetteWithRequiredYear(yearEnd);
            if (requiredRow.isEmpty()) {
                gazettesUnderCollection.remove(gazetteIssue);
                return GAZETTE_NOT_FOUND_CODE; //No gazette found matching the requirements
            }
        } else {
            this.url = buildTextSourceIdentifier();
        }

        Optional<WebElement> downloadPDFButton = findButton(requiredRow.get(), BUTTON_DOWNLOAD_STRING);
        if (downloadPDFButton.isPresent()) {
            clickElement(downloadPDFButton.get());
            log.info("Downloading {}", gazetteIssue);
            return DOWNLOADING_PDF_CODE; //PDF downloading
        } else {
            gazettesUnderCollection.remove(gazetteIssue);
            return NO_TEXT_BUTTONS_FOUND_CODE; //No Details Page or PDF button found
        }
    }

    private String buildTextSourceIdentifier() {
        return String.format(TEXT_SOURCE_IDENTIFIER_FORMATTER, gazetteIssue, identifier);
    }

    /**
     * reads vital information from NoSuchElementException
     */
    private String getNoElementInformation(String errorMessage) {
        Pattern regex = Pattern.compile("\\{\"method\":\"(.*?)\",\"selector\":\"(.*?)\"}");

        Matcher m = regex.matcher(errorMessage);
        if (m.find()) {
            return String.format("%s by %s", m.group(2), m.group(1));
        }
        return "No further information";
    }

    private String findLegislationInIssue() throws DataCollectionException {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("form table div a")));
            Optional<WebElement> billLink = browser.findElements(By.cssSelector("form table div a")).stream()
                    .filter(link -> {
                        String text = link.getText();
                        return type.equals(GazetteType.LAW) ? isGivenLaw(text) : isGivenBill(text);
                    })
                    .findFirst();
            if (billLink.isPresent()) {
                String linkText = billLink.get().getText();
                clickElement(billLink.get());
                return linkText;
            } else {
                log.warn("Legislation {} not found", identifier);
                throw new DataCollectionException(String.format("No legislation found with given identifier (%s)", identifier));
            }
        } catch (TimeoutException timeout) {
            log.error("Gazette table of contents did not load");
            throw new DataCollectionException("Issue with finding legislation in gazette: Gazette content did not load");
        } catch (DataCollectionException ex) {
            throw new DataCollectionException(String.format("Issue with finding legislation in gazette: %s", ex.getMessage()));
        }
    }

    /**
     * Tries to match bill id. In the text it can be number and year or number and full date (only one type in one text)
     * First it finds all ids from the text with the regexes, then it checks if it matches either the house or senate bill id
     */
    private boolean isGivenBill(String text) {
        List<ImmutablePair<Integer, Integer>> textBillIds = new ArrayList<>();
        List<Pattern> regexes = List.of(GAZETTE_CONTENT_BILL_SELECT_REGEX, GAZETTE_CONTENT_BILL_FULL_DATE_SELECT_REGEX);
        int regexIndex = 0;
        while (textBillIds.isEmpty()) {
            Matcher linkTextMatcher = regexes.get(regexIndex).matcher(text);
            while (linkTextMatcher.find()) {
                int idNumber = Integer.parseInt(linkTextMatcher.group(1));
                int yearEnd = Integer.parseInt(linkTextMatcher.group(2));
                textBillIds.add(new ImmutablePair<>(idNumber, yearEnd));
            }
            regexIndex++;
            if (regexIndex >= regexes.size()) {
                break;
            }
        }
        if (textBillIds.isEmpty()) {
            return false;
        }
        Matcher billIdMatcher = BILL_ID_REGEX.matcher(identifier);
        if (!billIdMatcher.find()) {
            return false;
        }
        String houseId = billIdMatcher.group(1);
        String senateId = billIdMatcher.group(2);
        return Stream.of(houseId, senateId)
                .filter(id -> !id.isBlank())
                .map(id -> {
                    String[] splitId = id.split("/");
                    int idNumber = Integer.parseInt(splitId[0]);
                    int yearEnd = Integer.parseInt(splitId[1]);
                    return new ImmutablePair<>(idNumber, yearEnd);
                })
                .anyMatch(billIdPair -> {
                    for (ImmutablePair<Integer, Integer> textBillId : textBillIds) {
                        if (billIdPair.getKey().equals(textBillId.getKey()) && billIdPair.getValue().equals(textBillId.getValue())) {
                            return true;
                        }
                        if (secondAttempt && billIdPair.getKey().equals(textBillId.getKey())) {//if only year is different
                            return true;
                        }
                    }
                    return false;
                });
    }

    private boolean isGivenLaw(String text) {
        //Usually it has the law_number and full date of the passing
        Matcher linkTextMatcher = GAZETTE_CONTENT_LAW_SELECT_REGEX.matcher(text);
        if (!linkTextMatcher.find()) {
            //But it can have only the law number and the year
            linkTextMatcher = Pattern.compile("(\\d+)\\s*[dD][eE]\\s*(\\d+)").matcher(text);
            if (!linkTextMatcher.find()) {
                return false;
            }
        }
        Matcher lawIdMatcher = LAW_ID_REGEX.matcher(identifier);
        if (!lawIdMatcher.find()) {
            return false;
        }
        String lawNumber = lawIdMatcher.group(2);
        String lawYear = lawIdMatcher.group(1);
        return Integer.parseInt(lawNumber) == Integer.parseInt(linkTextMatcher.group(1)) &&
                Integer.parseInt(lawYear) == Integer.parseInt(linkTextMatcher.group(2));
    }

    private String getLink() {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("formGacetaPublica:textLink")));
            return browser.findElement(By.id("formGacetaPublica:textLink")).getText();
        } catch (TimeoutException ex) {
            return buildTextSourceIdentifier();
        } catch (Exception ex) {
            return String.format("Error while trying to get link: %s", ex);
        }
    }

    private String getText(String linkText) throws DataCollectionException {
        if (type.equals(GazetteType.AMENDMENT)) {
            return getAmendmentText(linkText);
        }
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("/html/body/div[2]/div/table/tbody/tr[4]/td/form/div/div/div/div[2]/div[2]/label")));
            WebElement textRootElement = browser.findElement(By.xpath("/html/body/div[2]/div/table/tbody/tr[4]/td/form/div/div/div/div[2]/div[2]"));
            String billRawText = textRootElement.getText();
            saveTextAutoIdentifier(billRawText);
            return getRequiredPartOfText(billRawText);

        } catch (TimeoutException timeout) {
            log.error("Gazette text did not load");
            throw new DataCollectionException("Issue with extracting the text: Text did not load");
        } catch (Exception ex) {
            throw new DataCollectionException(String.format("Issue with extracting the text: %s", ex.getMessage()));
        }
    }

    /**
     * Saves text with handlers text source type and automatically generated identifier
     */
    private TextSource saveTextAutoIdentifier(String text) {
        return saveText(text, textSourceType, buildTextSourceIdentifier());
    }

    private TextSource saveText(String text, String type, String identifier) {
        TextSource source = new TextSource();
        source.setCountry(Country.COLOMBIA);
        source.setTextType(type);
        source.setTextIdentifier(identifier);
        source.setTextContent(text);
        source.setDownloadUrl(this.url);
        NOT_YET_SAVED_TEXTS.add(source);
        return source;
    }

    private String getRequiredPartOfText(String text) throws DataCollectionException {
        Matcher startTextMatcher = START_TEXT_REGEX.matcher(text);
        if (startTextMatcher.find()) {
            try {
                return getTextTillRegex(getEndRegex(), text, startTextMatcher.end());
            } catch (DataCollectionException ex) {
                log.warn("While scraping {} text: {}", type, ex.getMessage());
                return textTrim(text.substring(startTextMatcher.end()));
            }

        } else {
            log.warn("DECRETA not found");
            startTextMatcher = ARTICLE_START_TEXT_REGEX.matcher(text);
            if (startTextMatcher.find()) {
                try {
                    return getTextTillRegex(getEndRegex(), text, startTextMatcher.start());
                } catch (DataCollectionException ex) {
                    log.warn("While scraping {} text: {}", type, ex.getMessage());
                    return textTrim(text.substring(startTextMatcher.start()));
                }
            } else {
                throw new DataCollectionException("Neither DECRETA or the first article found, cannot determine the start of the text extraction");
            }
        }
    }

    private String getTextTillRegex(Pattern regex, String text) throws DataCollectionException {
        return getTextTillRegex(regex, text, 0);
    }

    private String getTextTillRegex(Pattern regex, String text, int startFrom) throws DataCollectionException {
        Matcher endTextMatcher = regex.matcher(text);
        boolean hasEndText = endTextMatcher.find();
        if (hasEndText &&
                startFrom < endTextMatcher.start()) {
            return textTrim(text.substring(startFrom, endTextMatcher.start()));
        } else {
            if (!hasEndText) {
                throw new DataCollectionException("Text does not contain regex");
            } else {
                throw new DataCollectionException("End regex found earlier than it is required");
            }
        }
    }

    private String getAmendmentText(String linkText) throws DataCollectionException {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("/html/body/div[2]/div/table/tbody/tr[4]/td/form/div/div/div/div[2]/div[2]/label")));
            WebElement textRootElement = browser.findElement(By.xpath("/html/body/div[2]/div/table/tbody/tr[4]/td/form/div/div/div/div[2]/div[2]/label"));
            List<WebElement> paragraphs;
            try {
                paragraphs = textRootElement.findElement(By.className("Section1")).findElements(By.xpath("./*[not(self::table)]"));
            } catch (NoSuchElementException ex) {
                paragraphs = textRootElement.findElements(By.xpath("./*[not(self::table)]"));
            }
            List<String> paragraphTexts = paragraphs.stream().map(WebElement::getText).collect(Collectors.toList());
            String rawText = StringUtils.join(paragraphTexts, '\n');
            saveTextAutoIdentifier(rawText);
            saveLinkText(linkText);
            return getRequiredPartOfAmendmentText(rawText, linkText);
        } catch (TimeoutException timeout) {
            log.error("Amendment text did not load");
            throw new DataCollectionException("Issue with extracting the text: Text did not load");
        }
    }

    // Link text could mean that the whole text is needed if it contains the required pattern
    // (TEXTO DEFINITIVO APROBADO) so it has to be saved as well.
    private TextSource saveLinkText(String linkText) {
        String textType = TEXT_TYPE_TO_LINK_TEXT_TYPE.get(textSourceType);
        return saveText(linkText, textType, buildTextSourceIdentifier());
    }

    private String getRequiredPartOfAmendmentText(String rawText, String linkText) throws DataCollectionException {
        List<String> paragraphs = Arrays.asList(rawText.split("\n"));
        Optional<Integer> startIndex = getAmendmentStartIndex(linkText, paragraphs);
        int startFrom = startIndex.orElseThrow(() -> new DataCollectionException("Not found start text TEXTO (DEFINITIVO) APROBADO for amendment in gazette"));

        String text = buildTextForAmendment(paragraphs, startFrom);
        Matcher startTextMatcher = START_TEXT_REGEX.matcher(text);
        if (startTextMatcher.find()) {
            return textTrim(text.substring(startTextMatcher.end()));
        }
        startTextMatcher = ARTICLE_START_TEXT_REGEX.matcher(text);
        if (startTextMatcher.find()) {
            return textTrim(text.substring(startTextMatcher.start()));
        }
        throw new DataCollectionException("Neither DECRETA or the first article found, cannot determine the start of the text extraction");
    }

    private Optional<Integer> getAmendmentStartIndex(String linkText, List<String> paragraphs) {
        int secondaryStartIndex = -1;
        if (linkText.replaceAll("\\s+", " ").toLowerCase().contains("texto definitivo aprobado")
                || linkText.replaceAll("\\s+", " ").toLowerCase().contains("texto aprobado")
                || linkText.replaceAll("\\s+", " ").toLowerCase().contains("texto definitivo")) {
            return Optional.of(0);

        } else {
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = paragraphs.get(i);
                //Checking for capitalized as that means the most uniformly the paragraph is a title
                if (text.trim().startsWith("TEXTO DEFINITIVO APROBADO")) {
                    return Optional.of(i);
                } else if (secondaryStartIndex == -1 &&
                        (text.trim().startsWith("TEXTO APROBADO") || text.trim().startsWith("TEXTO DEFINITIVO"))) {
                    secondaryStartIndex = i;
                }
            }
        }
        //When the main search term not found but the secondary is
        if (secondaryStartIndex != -1) {
            return Optional.of(secondaryStartIndex);
        }
        return Optional.empty();
    }

    private String buildTextForAmendment(List<String> paragraphs, int startFrom) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = startFrom; i < paragraphs.size(); i++) {
            String paragraphText = paragraphs.get(i);
            if (AMENDMENT_TEXT_END_REGEX.matcher(paragraphText).find()) {
                break;
            }
            stringBuilder.append(paragraphText);
            stringBuilder.append("\n");
        }
        return stringBuilder.toString();
    }

    private String textTrim(String text) {
        String trimmedText;
        trimmedText = text.replaceAll("^[^a-zA-Z]*", "").trim();
        return trimmedText;
    }

    /**
     * Searches for identifier number, than for the end regex and only if both is found it gets the text between them
     * and tries to extract the required part.
     * This is how it tries to differentiate between bills. (And amendments can't be processed as their end text never
     * appear in pdfs)
     */
    private String getTextFromPDF(String pdfText) throws DataCollectionException {
        if (type.equals(GazetteType.AMENDMENT)) {
            throw new DataCollectionException("Cannot process PDF for amendment");
        }

        List<String> errors = new ArrayList<>();
        List<ImmutablePair<String, String>> idParts = getSeparatedIds();
        Pattern endRegex = getEndRegex();

        for (ImmutablePair<String, String> parts : idParts) {
            if (Objects.isNull(parts.getValue()) || parts.getValue().isBlank()) {
                continue;
            }
            String number = parts.getKey();
            String year = parts.getValue();
            String baseRegex = "%s\\s*[dD][eE][\\s\\d]*%s";
            final Pattern PDF_SELECT_REGEX = Pattern.compile(String.format(baseRegex, number, year));
            String text = pdfText.replaceAll("\\s+", " ").trim();
            Matcher billStartMatcher = PDF_SELECT_REGEX.matcher(text);

            if (billStartMatcher.find()) {
                String subText = text.substring(billStartMatcher.start());
                Matcher endTextMatcher = endRegex.matcher(subText);
                if (endTextMatcher.find()) {
                    String subTextWithEnd = subText.substring(0, endTextMatcher.end()).trim();
                    saveTextAutoIdentifier(subTextWithEnd);
                    return getRequiredPartOfText(subTextWithEnd);
                } else {
                    log.error(String.format("Could not find the end of the %s in PDF with the id: %s de %s", type, number, year));
                }
            } else {
                log.error(String.format("Not found %s in PDF with id: %s de %s", type, number, year));
            }
        }
        throw new DataCollectionException(String.join(" & ", errors));
    }

    /**
     * Separates the year numbers from the id numbers in the identifier to build the pdf bill start regex
     */
    private List<ImmutablePair<String, String>> getSeparatedIds() throws DataCollectionException {
        if (type.equals(GazetteType.BILL)) {
            Matcher billIdMatcher = BILL_ID_REGEX.matcher(identifier);
            billIdMatcher.find();
            return Stream.of(billIdMatcher.group(1), billIdMatcher.group(2))
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .map(text -> text.split("/"))
                    .map(splitBillId -> new ImmutablePair<>(splitBillId[0], splitBillId[1]))
                    .toList();
        } else if (type.equals(GazetteType.LAW)) {
            Matcher lawIdMatcher = LAW_ID_REGEX.matcher(identifier);
            lawIdMatcher.find();
            return new ArrayList<>(Collections.singleton(new ImmutablePair<>(lawIdMatcher.group(2), lawIdMatcher.group(1))));
        } else if (type.equals(GazetteType.AMENDMENT)) {
            log.warn("Cannot process pdf for amendment");
            throw new DataCollectionException("Cannot process PDF for amendment");
        } else {
            throw new DataCollectionException("Unsupported gazette search type");
        }
    }

    private Pattern getEndRegex() throws DataCollectionException {
        if (type.equals(GazetteType.BILL)) {
            return BILL_TEXT_END_REGEX;
        } else if (type.equals(GazetteType.LAW)) {
            return LAW_TEXT_END_REGEX;
        } else if (type.equals(GazetteType.AMENDMENT)) {
            return AMENDMENT_TEXT_END_REGEX;
        } else {
            throw new DataCollectionException("Unsupported gazette search type");
        }
    }

    private String getWholePDFText() throws TimeoutException, DataCollectionException {
        Wait<String> waitForExistingInDB =
                new FluentWait<>(gazetteIssue).withTimeout(Duration.ofSeconds(600)).pollingEvery(Duration.ofSeconds(10));
        Optional<String> text = waitForExistingInDB.until(gazette -> {
            Optional<DownloadedFile> source = tryGettingFile();
            if (source.isPresent()) {
                this.url = source.get().getUrl();
                return readService.getPdfTextContent(source);
            }
            log.info("Waiting for {} PDF to be saved to the database", gazetteIssue);
            //PDF not yet in database
            return null;
        });
        log.info("Got PDF from database");
        return text.orElseThrow(() -> new DataCollectionException("Text couldn't be read from the database PDF"));
    }
}
