import javafx.application.{Application, Platform}
import javafx.stage.{FileChooser, Stage}
import javafx.scene.Scene
import javafx.scene.control._
import javafx.scene.layout._
import javafx.geometry.{Insets, Pos}
import javafx.collections.{FXCollections, ObservableList}
import javafx.scene.chart.{BarChart, CategoryAxis, NumberAxis, PieChart, XYChart}

import java.io.{File, PrintWriter}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}
import helpers.JsonIO
import data.{AnalysisConfig, DeadCodeReport, MethodWithDeadCode}
import org.opalj.ai.common.DomainRegistry
import org.opalj.log.GlobalLogContext
import com.typesafe.config.ConfigFactory
import play.api.libs.json._

import scala.jdk.CollectionConverters._
import helpers.DeadCodeAnalysis
import org.opalj.br.analyses.Project

import java.net.URL
import scala.util.Random

/**
 * Main entry point for the Dead Code Analysis GUI Application
 * Launches the JavaFX application with command line arguments
 */
object DeadCodeGUIAppMain {
  def main(args: Array[String]): Unit = {
    Application.launch(classOf[DeadCodeGUIApp], args: _*)
  }
}

/**
 * Helper case class to represent a row of data in grid layouts
 * @param label The label text for the row
 * @param value The value text for the row
 * @param valueStyle Optional CSS styling for the value
 */
private case class GridRowData(label: String, value: String, valueStyle: String = "")

/**
 * Helper case class to represent a method with dead code along with domain information
 * Used specifically for multi-domain analysis results display
 * @param method The method containing dead code
 * @param domainIndices List of domain indices where this method was found
 */
private case class MethodWithDeadCodeAndDomains(method: MethodWithDeadCode, domainIndices: List[Int])

/**
 * Main JavaFX Application class for Dead Code Analysis GUI
 * Provides a comprehensive interface for configuring, running, and visualizing dead code analysis
 */
class DeadCodeGUIApp extends Application {
  private var selectedConfigFile: Option[File] = None
  private var projectDirectory: Option[File] = None
  private var selectedDomainIndex: Int = 0
  private var lastAnalysisResult: Option[DeadCodeReport] = None
  private var lastMultiAnalysisResults: List[DeadCodeReport] = List.empty

  /**
   * Utility function to populate a GridPane with labeled data rows
   * @param grid The GridPane to populate
   * @param rows List of GridRowData containing labels, values, and optional styling
   */
  private def populateGrid(grid: GridPane, rows: List[GridRowData]): Unit = {
    rows.zipWithIndex.foreach { case (rowData, index) =>
      val labelNode = new Label(rowData.label + ":")
      labelNode.setStyle("-fx-font-weight: bold;")
      val valueNode = new Label(rowData.value)
      if (rowData.valueStyle.nonEmpty) valueNode.setStyle(rowData.valueStyle)
      grid.add(labelNode, 0, index)
      grid.add(valueNode, 1, index)
    }
  }

  /**
   * JavaFX Application start method - initializes the main UI
   * @param primaryStage The primary stage for this application
   */
  override def start(primaryStage: Stage): Unit = {
    // Create main tab pane
    val tabPane = new TabPane()

    // Analysis Tab
    val analysisTab = new Tab("Analysis", createAnalysisPane(primaryStage))
    analysisTab.setClosable(false)

    // Results Tab
    val resultsTab = new Tab("Results Viewer", createResultsPane())
    resultsTab.setClosable(false)
    resultsTab.setDisable(true) // Initially disabled until analysis is complete

    tabPane.getTabs.addAll(analysisTab, resultsTab)

    val scene = new Scene(tabPane, 1280, 720)

    primaryStage.setTitle("Dead Code Analysis Tool - GUI")
    primaryStage.setScene(scene)
    primaryStage.show()

    primaryStage.setOnCloseRequest(_ => {
      Platform.exit()
      System.exit(0)
    })
    primaryStage.setMaximized(true)
  }

  /**
   * Creates the analysis configuration pane with all controls for setting up and running analysis
   * @param primaryStage Reference to primary stage for file dialogs
   * @return VBox containing all analysis controls
   */
  private def createAnalysisPane(primaryStage: Stage): VBox = {
    // Main control buttons
    val selectButton = new Button("Choose Config File")
    val autoDetectButton = new Button("Auto-Detect Configs")
    val loadResultButton = new Button("Load results from JSON")
    val runButton = new Button("Run Analysis")

    // Domain selection controls
    val domainCheckListView = new ListView[CheckBox]()
    domainCheckListView.setPrefHeight(250)
    domainCheckListView.setVisible(false)

    // Status and information labels
    val statusLabel = new Label("No config selected.")
    statusLabel.setStyle("-fx-font-weight: bold;")

    val projectPathLabel = new Label("Current directory: " + System.getProperty("user.dir"))
    projectPathLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;")

    // Configuration file list view for auto-detection
    val configListView = new ListView[String]()
    configListView.setPrefHeight(200)
    configListView.setVisible(false)

    // Domain selection section setup
    val domainSection = new VBox(5)
    val domainLabel = new Label("Domain Selection:")
    domainLabel.setStyle("-fx-font-weight: bold;")

    // Radio buttons for domain selection modes
    val domainModeToggle = new ToggleGroup()
    val autoModeRadio = new RadioButton("Automatic (first available domain)")
    val manualModeRadio = new RadioButton("Manual (select from list below)")
    val multiModeRadio = new RadioButton("Multiple Domains (select multiple from list)")

    // Configure radio button group
    autoModeRadio.setToggleGroup(domainModeToggle)
    manualModeRadio.setToggleGroup(domainModeToggle)
    multiModeRadio.setToggleGroup(domainModeToggle)
    autoModeRadio.setSelected(true)

    // Dropdown for manual domain selection
    val domainComboBox = new ComboBox[String]()
    domainComboBox.setPrefWidth(500)
    domainComboBox.setVisible(false)

    // Populate domain list from OPAL registry
    val domainDescriptions = DomainRegistry.domainDescriptions.toList
    val domainItems: ObservableList[String] = FXCollections.observableArrayList()
    domainDescriptions.zipWithIndex.foreach { case (description, index) =>
      domainItems.add(s"[$index] $description")
    }
    domainComboBox.setItems(domainItems)
    domainComboBox.getSelectionModel.selectFirst()

    // Create checkboxes for multi-domain selection
    val domainCheckBoxes: ObservableList[CheckBox] = FXCollections.observableArrayList()
    domainDescriptions.zipWithIndex.foreach { case (description, index) =>
      val checkBox = new CheckBox(s"[$index] $description")
      domainCheckBoxes.add(checkBox)
    }
    domainCheckListView.setItems(domainCheckBoxes)

    // Information label for domain selection
    val domainInfoLabel = new Label("Automatic mode uses the first available domain. Manual and Multiple mode lets you choose.")
    domainInfoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;")

    // Control buttons for multiple domain selection
    val multiDomainButtonBox = new HBox(10)
    multiDomainButtonBox.setAlignment(Pos.CENTER)
    multiDomainButtonBox.setVisible(false)

    // Select All button for multi-domain mode
    val selectAllButton = new Button("Select All")
    selectAllButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 11px;")
    selectAllButton.setPrefWidth(100)

    // Deselect All button for multi-domain mode
    val deselectAllButton = new Button("Deselect All")
    deselectAllButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px;")
    deselectAllButton.setPrefWidth(100)

    // Select Random button for multi-domain mode
    val selectRandomButton = new Button("Select Random")
    selectRandomButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 11px;")
    selectRandomButton.setPrefWidth(100)

    multiDomainButtonBox.getChildren.addAll(selectAllButton, deselectAllButton, selectRandomButton)

    // Button action handlers for multi-domain controls
    selectAllButton.setOnAction(_ => {
      domainCheckBoxes.asScala.foreach(_.setSelected(true))
    })

    deselectAllButton.setOnAction(_ => {
      domainCheckBoxes.asScala.foreach(_.setSelected(false))
    })

    selectRandomButton.setOnAction(_ => {
      // First deselect all, then randomly select a subset
      domainCheckBoxes.asScala.foreach(_.setSelected(false))
      val randomCount = Math.max(1, Random.nextInt(domainCheckBoxes.size() / 2) + 1)
      val shuffled = domainCheckBoxes.asScala.toList.sortBy(_ => Random.nextDouble())
      shuffled.take(randomCount).foreach(_.setSelected(true))
    })

    // Radio button event handlers to show/hide appropriate controls
    autoModeRadio.setOnAction(_ => {
      domainComboBox.setVisible(false)
      domainCheckListView.setVisible(false)
      multiDomainButtonBox.setVisible(false)
    })

    manualModeRadio.setOnAction(_ => {
      domainComboBox.setVisible(true)
      domainCheckListView.setVisible(false)
      multiDomainButtonBox.setVisible(false)
    })

    multiModeRadio.setOnAction(_ => {
      domainComboBox.setVisible(false)
      domainCheckListView.setVisible(true)
      multiDomainButtonBox.setVisible(true)
    })

    // Handle domain selection changes in manual mode
    domainComboBox.setOnAction(_ => {
      selectedDomainIndex = domainComboBox.getSelectionModel.getSelectedIndex
    })

    // Assemble domain selection section
    domainSection.getChildren.addAll(
      domainLabel,
      autoModeRadio,
      manualModeRadio,
      multiModeRadio,
      domainComboBox,
      domainCheckListView,
      multiDomainButtonBox,
      domainInfoLabel
    )

    // Analysis log output area
    val logArea = new TextArea()
    logArea.setEditable(false)
    logArea.setPrefRowCount(40)
    logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;")

    // Initially disable run button until config is selected
    runButton.setDisable(true)

    // Set initial project directory to current working directory
    projectDirectory = Some(new File(System.getProperty("user.dir")))

    // Button Action Handlers

    /**
     * Config file selection button handler
     * Opens file chooser dialog for manual config selection
     */
    selectButton.setOnAction(_ => {
      val chooser = new FileChooser()
      chooser.setTitle("Select Config JSON File")
      chooser.getExtensionFilters.add(
        new FileChooser.ExtensionFilter("JSON Config Files", "*.json")
      )

      // Set initial directory based on current project directory
      val initialDir = projectDirectory.getOrElse(new File(System.getProperty("user.dir")))
      if (initialDir.exists() && initialDir.isDirectory) {
        chooser.setInitialDirectory(initialDir)
      }

      val selected = chooser.showOpenDialog(primaryStage)
      if (selected != null) {
        // Update application state with selected config
        selectedConfigFile = Some(selected)
        projectDirectory = Some(selected.getParentFile) // Update project directory
        projectPathLabel.setText(s"Current directory: ${selected.getParent}")
        statusLabel.setText(s"Selected config: ${selected.getName}")
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;")
        runButton.setDisable(false)
        configListView.setVisible(false)
        logArea.appendText(s"Config file selected: ${selected.getAbsolutePath}\n")
      }
    })

    loadResultButton.setOnAction(_ => {
      val chooser = new FileChooser()
      chooser.setTitle("Select results JSON file")
      chooser.getExtensionFilters.add(
        new FileChooser.ExtensionFilter("JSON Result Files", "*.json")
      )

      // Set initial directory based on current project directory
      val initialDir = projectDirectory.getOrElse(new File(System.getProperty("user.dir")))
      if (initialDir.exists() && initialDir.isDirectory) {
        chooser.setInitialDirectory(initialDir)
      }
      val selected = chooser.showOpenDialog(primaryStage)

      logArea.appendText(s"Selected JSON results file: $selected\n")

      val tabPane = primaryStage.getScene.getRoot.asInstanceOf[TabPane]
      val resultsTab = tabPane.getTabs.get(1)

      if (selected != null) {
        val source = scala.io.Source.fromFile(selected)
        val jsonContents = try Json.parse(source.mkString) finally source.close()
        val reportResult = jsonContents.validate[DeadCodeReport]
        val report = reportResult.getOrElse(null)
        if (report != null) {
          lastAnalysisResult = Some(report)
          resultsTab.setDisable(false)
          refreshResultsVisualization()
          logArea.appendText(s"Generated report from JSON file: $selected\n")
        } else{
          logArea.appendText(s"File could not be decoded properly: $selected\n")
          lastAnalysisResult = None
          resultsTab.setDisable(true)
        }
      }
      else {
        resultsTab.setDisable(true)
      }
    })

    /**
     * Auto-detect configs button handler
     * Searches for JSON config files in project directory and subdirectories
     */
    autoDetectButton.setOnAction(_ => {
      val searchDir = projectDirectory.getOrElse(new File(System.getProperty("user.dir")))
      val configs = findConfigFiles(searchDir)

      if (configs.nonEmpty) {
        // Display found configs in ListView
        val configNames: ObservableList[String] = FXCollections.observableArrayList()
        configs.foreach(config => configNames.add(s"${config.getName} (${config.getParent})"))

        configListView.setItems(configNames)
        configListView.setVisible(true)

        statusLabel.setText(s"Found ${configs.length} config file(s). Double-click to select:")
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;")

        logArea.appendText(s"Auto-detected ${configs.length} config files:\n")
        configs.foreach(config => logArea.appendText(s"  - ${config.getAbsolutePath}\n"))

        // Handle double-click selection from ListView
        configListView.setOnMouseClicked(event => {
          if (event.getClickCount == 2) {
            val selectedIndex = configListView.getSelectionModel.getSelectedIndex
            if (selectedIndex >= 0 && selectedIndex < configs.length) {
              selectedConfigFile = Some(configs(selectedIndex))
              projectDirectory = Some(configs(selectedIndex).getParentFile)
              projectPathLabel.setText(s"Current directory: ${configs(selectedIndex).getParent}")
              statusLabel.setText(s"Selected config: ${configs(selectedIndex).getName}")
              statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;")
              runButton.setDisable(false)
              logArea.appendText(s"Selected: ${configs(selectedIndex).getAbsolutePath}\n")
            }
          }
        })
      } else {
        // No configs found
        statusLabel.setText("No config files found in the current directory!")
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;")
        configListView.setVisible(false)
        logArea.appendText(s"No .json config files found in: ${searchDir.getAbsolutePath}\n")
      }
    })

    /**
     * Run analysis button handler
     * Main analysis execution logic supporting both single and multi-domain modes
     */
    runButton.setOnAction(_ => {
      selectedConfigFile match {
        case Some(configFile) =>
          // Get reference to results tab for enabling after analysis
          val tabPane = primaryStage.getScene.getRoot.asInstanceOf[TabPane]
          val resultsTab = tabPane.getTabs.get(1)

          // Initialize log output
          logArea.clear()
          logArea.appendText("=" * 60 + "\n")
          logArea.appendText("STARTING DEAD CODE ANALYSIS\n")
          logArea.appendText("=" * 60 + "\n")
          logArea.appendText(s"Config file: ${configFile.getAbsolutePath}\n")
          logArea.appendText(s"Started at: ${java.time.LocalDateTime.now()}\n")

          // Determine analysis mode based on radio button selection
          val isMultiMode = multiModeRadio.isSelected
          val isManualMode = manualModeRadio.isSelected

          // Disable run button during analysis
          runButton.setDisable(true)
          runButton.setText("Running...")

          // Run analysis in separate thread to avoid UI blocking
          new Thread(() => {
            try {
              // Load and modify config to disable interactive mode
              val originalConfig = JsonIO.readJsonConfig(configFile.getAbsolutePath)
              val modifiedConfig = originalConfig.copy(interactive = false)

              if (isMultiMode) {
                // Multi-domain analysis mode
                val selectedIndices = domainCheckBoxes.asScala.zipWithIndex.filter(_._1.isSelected).map(_._2).toList

                // Validate domain selection
                if (selectedIndices.isEmpty) {
                  javafx.application.Platform.runLater(() => {
                    logArea.appendText("Error: No domains selected for multi-domain analysis!\n")
                    runButton.setDisable(false)
                    runButton.setText("Run Analysis")
                  })
                }

                // Log selected domains
                javafx.application.Platform.runLater(() => {
                  logArea.appendText(s"Multi-domain analysis mode - Selected ${selectedIndices.size} domains:\n")
                  selectedIndices.foreach { index =>
                    val domainDesc = DomainRegistry.domainDescriptions.toList(index)
                    logArea.appendText(s"  [$index] $domainDesc\n")
                  }
                  logArea.appendText("\n")
                })

                val multiResults = ListBuffer[DeadCodeReport]()

                // Run analysis for each selected domain
                selectedIndices.zipWithIndex.foreach { case (domainIndex, progressIndex) =>
                  javafx.application.Platform.runLater(() => {
                    logArea.appendText(s"Running analysis ${progressIndex + 1}/${selectedIndices.size} with domain [$domainIndex]...\n")
                  })

                  // Execute analysis for this domain
                  val tempConfigFile = createTempConfig(modifiedConfig)
                  val analysisResult = runAnalysisWithSelectedDomain(domainIndex, modifiedConfig)
                  multiResults += analysisResult
                  tempConfigFile.delete()

                  // Log individual result
                  javafx.application.Platform.runLater(() => {
                    logArea.appendText(s"  Completed: Found ${analysisResult.methodsFound.size} methods with dead code (${analysisResult.totalRuntimeMs}ms)\n")
                  })
                }

                // Store multi-analysis results and clear single result
                lastMultiAnalysisResults = multiResults.toList
                lastAnalysisResult = None // Clear single result

                // Final multi-domain analysis summary
                javafx.application.Platform.runLater(() => {
                  logArea.appendText(s"\nMulti-domain analysis completed successfully!\n")
                  logArea.appendText(s"Total analyses: ${multiResults.size}\n")
                  val totalMethods = multiResults.map(_.methodsFound.size).sum
                  val totalRuntime = multiResults.map(_.totalRuntimeMs).sum
                  logArea.appendText(s"Total methods with dead code found: $totalMethods\n")
                  logArea.appendText(s"Total runtime: ${totalRuntime}ms\n")
                  logArea.appendText("=" * 60 + "\n")

                  // Enable results tab and refresh visualization
                  resultsTab.setDisable(false)
                  refreshResultsVisualization()

                  // Re-enable run button
                  runButton.setDisable(false)
                  runButton.setText("Run Analysis")
                })

              } else {
                // Single domain analysis mode (existing functionality)
                val domainToUse = if (isManualMode) {
                  val selectedDomainText = domainComboBox.getSelectionModel.getSelectedItem
                  logArea.appendText(s"Domain selection mode: Manual - Selected: $selectedDomainText\n\n")
                  selectedDomainIndex
                } else {
                  logArea.appendText(s"Domain selection mode: Automatic - Using first available domain\n\n")
                  0
                }

                // Log analysis configuration
                javafx.application.Platform.runLater(() => {
                  logArea.appendText("Configuration loaded successfully.\n")
                  logArea.appendText(s"Project JARs: ${modifiedConfig.projectJars.size} files\n")
                  logArea.appendText(s"Library JARs: ${modifiedConfig.libraryJars.size} files\n")
                  logArea.appendText(s"Selected domain index: $domainToUse\n\n")
                  logArea.appendText("Starting analysis engine...\n")
                })

                // Execute single domain analysis
                val tempConfigFile = createTempConfig(modifiedConfig)
                val analysisResult = runAnalysisWithSelectedDomain(domainToUse, modifiedConfig)

                // Store result and clear multi results
                lastAnalysisResult = Some(analysisResult)
                lastMultiAnalysisResults = List.empty // Clear multi results

                tempConfigFile.delete()

                // Log completion and enable results
                javafx.application.Platform.runLater(() => {
                  logArea.appendText("\nAnalysis completed successfully!\n")
                  logArea.appendText(s"Found ${analysisResult.methodsFound.size} methods with dead code\n")
                  logArea.appendText(s"Total runtime: ${analysisResult.totalRuntimeMs}ms\n")
                  logArea.appendText("=" * 60 + "\n")

                  // Enable results tab and refresh visualization
                  resultsTab.setDisable(false)
                  refreshResultsVisualization()

                  // Re-enable run button
                  runButton.setDisable(false)
                  runButton.setText("Run Analysis")
                })
              }

            } catch {
              case ex: Exception =>
                // Handle analysis errors
                javafx.application.Platform.runLater(() => {
                  logArea.appendText(s"\nError running analysis: ${ex.getMessage}\n")
                  logArea.appendText(s"Stack trace: ${ex.getStackTrace.take(5).mkString("\n")}\n")
                  logArea.appendText("=" * 60 + "\n")
                  runButton.setDisable(false)
                  runButton.setText("Run Analysis")
                })
            }
          }).start()

        case None =>
          // No config file selected - show warning
          val alert = new Alert(Alert.AlertType.WARNING)
          alert.setTitle("Warning")
          alert.setHeaderText("No Config File Selected")
          alert.setContentText("Please select a configuration file first!")
          alert.showAndWait()
      }
    })

    // Layout Assembly
    val topSection = new VBox(5)
    topSection.getChildren.addAll(projectPathLabel)

    val buttonSection = new HBox(15)
    buttonSection.setAlignment(Pos.CENTER)
    buttonSection.getChildren.addAll(selectButton, autoDetectButton, loadResultButton)

    val statusSection = new VBox(5)
    statusSection.getChildren.addAll(statusLabel, configListView)

    val runSection = new VBox(10)
    runSection.setAlignment(Pos.CENTER)
    runSection.getChildren.addAll(domainSection, runButton)
    runButton.setPrefWidth(200)
    runButton.setPrefHeight(40)
    runButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;")

    val logSection = new VBox(5)
    val logLabel = new Label("Analysis Log:")
    logLabel.setStyle("-fx-font-weight: bold;")
    logSection.getChildren.addAll(logLabel, logArea)

    // Main layout assembly
    val mainLayout = new VBox(15)
    mainLayout.setPadding(new Insets(15))
    mainLayout.getChildren.addAll(
      topSection,
      buttonSection,
      statusSection,
      runSection,
      logSection
    )

    // Set growth properties for responsive layout
    VBox.setVgrow(logArea, Priority.ALWAYS)
    VBox.setVgrow(logSection, Priority.ALWAYS)
    mainLayout
  }

  // Results pane reference for dynamic updates
  private var resultsMainPane: VBox = _

  /**
   * Creates the initial results pane with placeholder content
   * Content is populated dynamically after analysis completion
   * @return VBox containing initial results pane layout
   */
  private def createResultsPane(): VBox = {
    resultsMainPane = new VBox(10)
    resultsMainPane.setPadding(new Insets(15))

    val titleLabel = new Label("Analysis Results Visualization")
    titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;")

    val noResultsLabel = new Label("No analysis results available yet. Run an analysis first.")
    noResultsLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;")

    resultsMainPane.getChildren.addAll(titleLabel, noResultsLabel)
    resultsMainPane
  }

  /**
   * Refreshes the results visualization based on current analysis results
   * Handles both single-domain and multi-domain analysis results
   * Updates the results pane with appropriate visualizations and data
   */
  private def refreshResultsVisualization(): Unit = {
    if (lastMultiAnalysisResults.nonEmpty) {
      // Multi-domain results visualization
      javafx.application.Platform.runLater(() => {
        resultsMainPane.getChildren.clear()

        // Title for multi-domain results
        val titleLabel = new Label("Multi-Domain Analysis Results")
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;")

        // Create tabbed interface for multi-domain results
        val combinedTabPane = new TabPane()

        // Combined Results Tab - shows union of all domain results
        val combinedTab = new Tab("Combined Results", createCombinedResultsPane(lastMultiAnalysisResults))
        combinedTab.setClosable(false)
        combinedTabPane.getTabs.add(combinedTab)

        // Multi-Domain Summary Tab - comparison across domains
        val multiSummarySection = createMultiDomainSummarySection(lastMultiAnalysisResults)
        val summaryTab = new Tab("Domain Summary", multiSummarySection)
        summaryTab.setClosable(false)
        combinedTabPane.getTabs.add(summaryTab)

        // Individual Domain Results Tabs
        lastMultiAnalysisResults.zipWithIndex.foreach { case (report, index) =>
          val domainTab = new Tab(s"Domain ${index + 1}", createSingleDomainPane(report))
          domainTab.setClosable(false)
          combinedTabPane.getTabs.add(domainTab)
        }

        // Scroll pane for all multi-domain content
        val scrollPane = new ScrollPane()
        val contentVBox = new VBox(15)
        contentVBox.setPadding(new Insets(10))
        contentVBox.getChildren.addAll(titleLabel, combinedTabPane)

        scrollPane.setContent(contentVBox)
        scrollPane.setFitToWidth(true)
        scrollPane.setStyle("-fx-background-color: transparent;")

        resultsMainPane.getChildren.add(scrollPane)
        VBox.setVgrow(scrollPane, Priority.ALWAYS)
      })
    } else {
      // Single domain result visualization (existing functionality)
      lastAnalysisResult match {
        case Some(report) =>
          javafx.application.Platform.runLater(() => {
            resultsMainPane.getChildren.clear()

            // Title for single domain results
            val titleLabel = new Label("Analysis Results Visualization")
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;")

            // Create visualization sections
            val summarySection = createSummarySection(report)
            val chartsSection = createChartsSection(report)
            val tableSection = createTableSection(report)

            // Scroll pane for all single domain content
            val scrollPane = new ScrollPane()
            val contentVBox = new VBox(15)
            contentVBox.setPadding(new Insets(10))
            contentVBox.getChildren.addAll(titleLabel, summarySection, chartsSection, tableSection)

            scrollPane.setContent(contentVBox)
            scrollPane.setFitToWidth(true)
            scrollPane.setStyle("-fx-background-color: transparent;")

            resultsMainPane.getChildren.add(scrollPane)
            VBox.setVgrow(scrollPane, Priority.ALWAYS)
          })

        case None =>
        // Should not happen since we check before calling this method
      }
    }
  }

  /**
   * Creates summary section for single domain analysis results
   * Displays key metrics and statistics from the analysis
   * @param report The dead code analysis report
   * @return VBox containing formatted summary information
   */
  private def createSummarySection(report: DeadCodeReport): VBox = {
    val section = new VBox(10)
    section.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;")

    val sectionTitle = new Label("Analysis Summary")
    sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;")

    val grid = new GridPane()
    grid.setHgap(20)
    grid.setVgap(8)

    // Calculate summary statistics
    val totalMethods = report.methodsFound.size
    val totalDeadInstructions = report.methodsFound.map(_.deadInstructions.size).sum
    val totalInstructions = report.methodsFound.map(_.numberOfTotalInstructions).sum
    val deadCodePercentage = if (totalInstructions > 0) totalDeadInstructions.toDouble / totalInstructions * 100 else 0.0

    // Format files list for display
    val filesList = if (report.filesAnalyzed.nonEmpty) {
      report.filesAnalyzed.map(_.split("/").last).mkString(", ")
    } else {
      "No files"
    }

    // Create summary data rows with conditional styling
    val rows = List(
      GridRowData("Files Analyzed", filesList),
      GridRowData("Domain Used", report.domainUsed),
      GridRowData("Analysis Runtime", s"${report.totalRuntimeMs}ms"),
      GridRowData("Analysis Finished", report.timeFinished.toString.split("T")(1).split("\\.")(0)),
      GridRowData("Methods with Dead Code", totalMethods.toString,
        if (totalMethods > 0) "-fx-text-fill: #d32f2f;" else "-fx-text-fill: #388e3c;"),
      GridRowData("Total Instructions", totalInstructions.toString),
      GridRowData("Total Dead Instructions", totalDeadInstructions.toString,
        if (totalDeadInstructions > 0) "-fx-text-fill: #d32f2f;" else ""),
      GridRowData("Dead Code Percentage", f"$deadCodePercentage%.2f%%",
        if (deadCodePercentage > 0) "-fx-text-fill: #d32f2f;" else "-fx-text-fill: #388e3c;")
    )

    populateGrid(grid, rows)
    section.getChildren.addAll(sectionTitle, grid)
    section
  }

  /**
   * Creates a summary section for the combined results of multi-domain analysis.
   * This includes statistics on total methods, unique methods (union), overlaps, and dead code percentage.
   * @param reports List of DeadCodeReport objects from all analyzed domains
   * @return VBox containing the summary UI
   */
  private def createCombinedSummarySection(reports: List[DeadCodeReport]): VBox = {
    // Create vertical container for the summary section
    val section = new VBox(10)
    section.setStyle("-fx-background-color: #fff3e0; -fx-padding: 15; -fx-background-radius: 5;")

    // Section title
    val sectionTitle = new Label("Combined Analysis Summary")
    sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;")

    // Grid for aligned summary data
    val grid = new GridPane()
    grid.setHgap(20)
    grid.setVgap(8)

    // Flatten all methods from all reports into a single list
    val allMethods = reports.flatMap(_.methodsFound)
    val uniqueMethodsMap = allMethods.groupBy(_.fullSignature)
    val uniqueMethods = uniqueMethodsMap.values.map { methodGroup =>
      methodGroup.maxBy(_.numberOfDeadInstructions)
    }.toList

    // Calculate combined statistics
    val totalCombinedMethods = uniqueMethods.size
    val totalCombinedDeadInstructions = uniqueMethods.map(_.deadInstructions.size).sum
    val totalCombinedInstructions = uniqueMethods.map(_.numberOfTotalInstructions).sum
    val combinedDeadCodePercentage = if (totalCombinedInstructions > 0)
      totalCombinedDeadInstructions.toDouble / totalCombinedInstructions * 100 else 0.0

    // Calculate raw method counts for overlap calculation
    val methodsPerDomain = reports.map(_.methodsFound.size)
    val totalMethodsAcrossDomains = methodsPerDomain.sum
    val overlapRatio = if (totalMethodsAcrossDomains > 0)
      (totalMethodsAcrossDomains - totalCombinedMethods).toDouble / totalMethodsAcrossDomains * 100 else 0.0

    // Compose all summary rows for the grid, with conditional styling
    val rows = List(
      GridRowData("Total Domains Combined", reports.size.toString),
      GridRowData("Total Methods Found (All Domains)", totalMethodsAcrossDomains.toString, "-fx-text-fill: #666666;"),
      GridRowData("Unique Methods with Dead Code (Union)", totalCombinedMethods.toString,
        if (totalCombinedMethods > 0) "-fx-text-fill: #d32f2f; -fx-font-weight: bold;" else "-fx-text-fill: #388e3c;"),
      GridRowData("Method Overlap Percentage", f"$overlapRatio%.1f%%", "-fx-text-fill: #ff9800;"),
      GridRowData("Combined Total Instructions", totalCombinedInstructions.toString),
      GridRowData("Combined Dead Instructions", totalCombinedDeadInstructions.toString,
        if (totalCombinedDeadInstructions > 0) "-fx-text-fill: #d32f2f;" else ""),
      GridRowData("Combined Dead Code Percentage", f"$combinedDeadCodePercentage%.2f%%",
        if (combinedDeadCodePercentage > 0) "-fx-text-fill: #d32f2f; -fx-font-weight: bold;" else "-fx-text-fill: #388e3c;")
    )

    populateGrid(grid, rows)
    section.getChildren.addAll(sectionTitle, grid)
    section
  }

  /**
   * Creates a visual analysis section for combined (multi-domain) results.
   * Displays both a pie chart (live vs dead instructions) and a bar chart (top dead code methods across all domains).
   * @param reports List of DeadCodeReport objects from all analyzed domains
   * @return VBox containing the combined charts section
   */
  private def createCombinedChartsSection(reports: List[DeadCodeReport]): VBox = {
    val section = new VBox(15)

    val sectionTitle = new Label("Combined Visual Analysis")
    sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;")

    val chartsHBox = new HBox(20)
    chartsHBox.setAlignment(Pos.CENTER)

    // Combined Pie Chart
    val combinedPieChart = createCombinedDeadCodePieChart(reports)

    // Combined Bar Chart
    val combinedBarChart = createCombinedTopMethodsBarChart(reports)

    // Add both charts to the horizontal box
    chartsHBox.getChildren.addAll(combinedPieChart, combinedBarChart)

    // Add section title and charts box to the vertical section
    section.getChildren.addAll(sectionTitle, chartsHBox)
    section
  }

  /**
   * Creates a pie chart visualizing the combined live vs dead instruction counts
   * across all analyzed domains (i.e., the union of unique methods).
   * Shows the proportion of "live" (reachable) and "dead" (unreachable) bytecode instructions.
   * @param reports List of DeadCodeReport objects from all analyzed domains
   * @return PieChart displaying the combined instruction statistics
   */
  private def createCombinedDeadCodePieChart(reports: List[DeadCodeReport]): PieChart = {
    // Collect all methods with dead code found in any report (any domain)
    val allMethods = reports.flatMap(_.methodsFound)

    // Group methods by their unique signature (to avoid duplicates across domains)
    val uniqueMethodsMap = allMethods.groupBy(_.fullSignature)

    // For each group of duplicates, select the instance with the most dead instructions
    val uniqueMethods = uniqueMethodsMap.values.map { methodGroup =>
      methodGroup.maxBy(_.numberOfDeadInstructions)
    }.toList

    // Calculate the total number of dead instructions in the unique set
    val totalDeadInstructions = uniqueMethods.map(_.deadInstructions.size).sum
    // Calculate the total number of instructions in the unique set
    val totalInstructions = uniqueMethods.map(_.numberOfTotalInstructions).sum
    // The rest are considered live instructions
    val liveInstructions = totalInstructions - totalDeadInstructions

    // Prepare the pie chart data: live vs. dead instructions
    val pieChartData = FXCollections.observableArrayList(
      new PieChart.Data(s"Live Instructions ($liveInstructions)", liveInstructions.toDouble),
      new PieChart.Data(s"Dead Instructions ($totalDeadInstructions)", totalDeadInstructions.toDouble)
    )

    // Create and configure the JavaFX PieChart object
    val pieChart = new PieChart(pieChartData)
    pieChart.setTitle("Combined Code Distribution (Union)")
    pieChart.setPrefSize(450, 400)
    pieChart.setLegendVisible(false)

    javafx.application.Platform.runLater(() => {
      if (pieChart.getData.size() >= 2) {
        pieChart.getData.get(0).getNode.setStyle("-fx-pie-color: #4CAF50;") // Live
        pieChart.getData.get(1).getNode.setStyle("-fx-pie-color: #f44336;")  // Dead
      }
    })

    // Return the finished chart to the caller
    pieChart
  }

  /**
   * Creates a bar chart showing the top 10 unique methods (across all domains)
   * with the highest number of dead instructions.
   * Only the union of unique methods is considered (duplicates across domains are removed).
   * @param reports List of DeadCodeReport objects from all analyzed domains
   * @return BarChart[String, Number] visualizing dead code density for top methods
   */
  private def createCombinedTopMethodsBarChart(reports: List[DeadCodeReport]): BarChart[String, Number] = {
    val xAxis = new CategoryAxis()
    val yAxis = new NumberAxis()
    val barChart = new BarChart[String, Number](xAxis, yAxis)

    xAxis.setLabel("Methods")
    yAxis.setLabel("Dead Instructions")
    barChart.setTitle("Top 10 Combined Methods with Most Dead Code (Union)")
    barChart.setPrefSize(500, 300)

    val series = new XYChart.Series[String, Number]()
    series.setName("Dead Instructions")

    val allMethods = reports.flatMap(_.methodsFound)

    val uniqueMethodsMap = allMethods.groupBy(_.fullSignature)
    val uniqueMethods = uniqueMethodsMap.values.map { methodGroup =>
      methodGroup.maxBy(_.numberOfDeadInstructions)
    }.toList

    val topMethods = uniqueMethods.sortBy(-_.deadInstructions.size).take(10)

    topMethods.foreach { method =>
      val methodName = method.fullSignature.split("\\(").head.split("\\.").last
      val shortName = if (methodName.length > 15) methodName.take(12) + "..." else methodName
      series.getData.add(new XYChart.Data(shortName, method.deadInstructions.size))
    }

    barChart.getData.add(series)
    barChart.setLegendVisible(false)
    barChart
  }

  /**
   * Creates a detailed results table for the combined (union) set of unique methods with dead code
   * across all analyzed domains.
   * For each method, displays class, method name, which domains it was found in, total/dead instructions, and dead code percentage.
   * Provides row click interactivity for viewing dead instruction details.
   * @param reports List of DeadCodeReport objects from all analyzed domains
   * @return VBox containing the combined detailed table and interactive detail panel
   */
  private def createCombinedTableSection(reports: List[DeadCodeReport]): VBox = {
    val section = new VBox(10)

    val sectionTitle = new Label("Combined Detailed Results (Union)")
    sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;")

    val mainContainer = new VBox(10)

    val tableView = new TableView[MethodWithDeadCodeAndDomains]()
    tableView.setPrefHeight(300)

    // Create columns
    val classColumn = new TableColumn[MethodWithDeadCodeAndDomains, String]("Class")
    classColumn.setCellValueFactory(data => new javafx.beans.property.SimpleStringProperty(
      data.getValue.method.enclosingTypeName.split("\\.").last
    ))
    classColumn.setPrefWidth(120)

    val methodColumn = new TableColumn[MethodWithDeadCodeAndDomains, String]("Method")
    methodColumn.setCellValueFactory(data => new javafx.beans.property.SimpleStringProperty(
      data.getValue.method.fullSignature.split("\\(").head.split("\\.").last
    ))
    methodColumn.setPrefWidth(200)

    val domainsColumn = new TableColumn[MethodWithDeadCodeAndDomains, String]("Found in Domains")
    domainsColumn.setCellValueFactory(data => new javafx.beans.property.SimpleStringProperty(
      data.getValue.domainIndices.mkString(", ")
    ))
    domainsColumn.setPrefWidth(120)

    val totalInstColumn = new TableColumn[MethodWithDeadCodeAndDomains, Number]("Total Instructions")
    totalInstColumn.setCellValueFactory(data => new javafx.beans.property.SimpleIntegerProperty(
      data.getValue.method.numberOfTotalInstructions
    ).asInstanceOf[javafx.beans.value.ObservableValue[Number]])
    totalInstColumn.setPrefWidth(150)

    val deadInstColumn = new TableColumn[MethodWithDeadCodeAndDomains, Number]("Dead Instructions")
    deadInstColumn.setCellValueFactory(data => new javafx.beans.property.SimpleIntegerProperty(
      data.getValue.method.numberOfDeadInstructions
    ).asInstanceOf[javafx.beans.value.ObservableValue[Number]])
    deadInstColumn.setPrefWidth(150)

    val percentColumn = new TableColumn[MethodWithDeadCodeAndDomains, String]("Dead %")
    percentColumn.setCellValueFactory(data => {
      val method = data.getValue.method
      val percentage = method.numberOfDeadInstructions.toDouble / method.numberOfTotalInstructions * 100
      new javafx.beans.property.SimpleStringProperty(f"$percentage%.1f%%")
    })
    percentColumn.setPrefWidth(100)

    tableView.getColumns.addAll(classColumn, methodColumn, domainsColumn, totalInstColumn, deadInstColumn, percentColumn)

    val allMethods = reports.zipWithIndex.flatMap { case (report, domainIndex) =>
      report.methodsFound.map(method => (method, domainIndex + 1))
    }

    // Group by full method signature, merge duplicates into one, collect all domain indices
    val groupedMethods = allMethods.groupBy(_._1.fullSignature).map { case (_, methods) =>
      val bestMethod = methods.maxBy(_._1.numberOfDeadInstructions)._1
      val domainIndices = methods.map(_._2).sorted.distinct
      MethodWithDeadCodeAndDomains(bestMethod, domainIndices)
    }.toList

    // ==== Row details: click to see dead instructions ====

    // Set up container for detailed instruction view (initially hidden)
    val detailsContainer = new VBox(5)
    val detailsLabel = new Label("Dead Instructions Details:")
    detailsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;")
    detailsLabel.setVisible(false)

    val instructionsList = new ListView[String]()
    instructionsList.setPrefHeight(200)
    instructionsList.setVisible(false)
    instructionsList.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;")

    detailsContainer.getChildren.addAll(detailsLabel, instructionsList)

    // Add row selection listener for showing/hiding details below the table
    tableView.getSelectionModel.selectedItemProperty().addListener((_, _, newValue) => {
      if (newValue != null) {
        showCombinedDeadInstructionsInline(newValue, detailsLabel, instructionsList)
      } else {
        hideDeadInstructionsInline(detailsLabel, instructionsList)
      }
    })

    // Prepare observable list and sort by number of dead instructions descending
    val items: ObservableList[MethodWithDeadCodeAndDomains] = FXCollections.observableArrayList()
    groupedMethods.sortBy(-_.method.numberOfDeadInstructions).foreach(items.add)
    tableView.setItems(items)

    // Add instruction for user
    val instructionLabel = new Label("💡 Click on any row to view detailed dead instructions. Shows union of all domains (duplicates removed).")
    instructionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-style: italic;")

    mainContainer.getChildren.addAll(tableView, detailsContainer, instructionLabel)
    section.getChildren.addAll(sectionTitle, mainContainer)
    section
  }



  /**
   * Displays the detailed list of dead instructions for a selected unique method
   * in the combined multi-domain results table.
   * Updates the details label and fills the instructions ListView for user inspection.
   * @param methodWithDomains The selected MethodWithDeadCodeAndDomains instance (includes domains info)
   * @param detailsLabel Label UI element for showing method info (updated in-place)
   * @param instructionsList ListView UI element for listing each dead instruction
   */
  private def showCombinedDeadInstructionsInline(methodWithDomains: MethodWithDeadCodeAndDomains, detailsLabel: Label, instructionsList: ListView[String]): Unit = {
    val method = methodWithDomains.method
    val domains = methodWithDomains.domainIndices.mkString(", ")

    detailsLabel.setText(s"Dead Instructions for: ${method.fullSignature.split("\\(").head.split("\\.").last} (Found in domains: $domains)")
    detailsLabel.setVisible(true)

    val instructionsItems: ObservableList[String] = FXCollections.observableArrayList()
    method.deadInstructions.foreach { deadInst =>
      instructionsItems.add(s"PC: ${deadInst.programCounter} - ${deadInst.stringRepresentation}")
    }
    instructionsList.setItems(instructionsItems)
    instructionsList.setVisible(true)
  }

  /**
   * Creates the complete results pane for multi-domain (combined) analysis.
   * Displays the combined summary, combined charts, and the combined detailed results table (union).
   * @param reports List of DeadCodeReport objects from all analyzed domains
   * @return VBox containing all combined results UI sections for visualization
   */
  private def createCombinedResultsPane(reports: List[DeadCodeReport]): VBox = {
    val pane = new VBox(15)
    pane.setPadding(new Insets(10))

    // Combined Summary Section
    val combinedSummary = createCombinedSummarySection(reports)

    // Combined Charts Section
    val combinedCharts = createCombinedChartsSection(reports)

    // Combined Table Section
    val combinedTable = createCombinedTableSection(reports)

    pane.getChildren.addAll(combinedSummary, combinedCharts, combinedTable)
    pane
  }

  /**
   * Creates the charts section for a single-domain analysis result.
   * Displays both a pie chart (live vs dead instructions) and a bar chart (top dead code methods) for the current report.
   * @param report The DeadCodeReport object representing the analysis result of a single domain
   * @return VBox containing the charts section UI
   */
  private def createChartsSection(report: DeadCodeReport): VBox = {
    val section = new VBox(15)

    val sectionTitle = new Label("Visual Analysis")
    sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;")

    val chartsHBox = new HBox(20)
    chartsHBox.setAlignment(Pos.CENTER)

    // Pie Chart - Dead vs Live Instructions
    val pieChart = createDeadCodePieChart(report)

    // Bar Chart - Top 10 Methods with Most Dead Code
    val barChart = createTopMethodsBarChart(report)

    chartsHBox.getChildren.addAll(pieChart, barChart)

    section.getChildren.addAll(sectionTitle, chartsHBox)
    section
  }

  /**
   * Creates a pie chart visualizing the proportion of live (reachable) and dead (unreachable) bytecode instructions
   * in a single-domain analysis result.
   * @param report The DeadCodeReport object representing the analysis result of a single domain
   * @return PieChart visualizing live vs. dead instruction distribution
   */
  private def createDeadCodePieChart(report: DeadCodeReport): PieChart = {
    val totalDeadInstructions = report.methodsFound.map(_.deadInstructions.size).sum
    val totalInstructions = report.methodsFound.map(_.numberOfTotalInstructions).sum
    val liveInstructions = totalInstructions - totalDeadInstructions

    val pieChartData = FXCollections.observableArrayList(
      new PieChart.Data(s"Live Instructions ($liveInstructions)", liveInstructions.toDouble),
      new PieChart.Data(s"Dead Instructions ($totalDeadInstructions)", totalDeadInstructions.toDouble)
    )

    val pieChart = new PieChart(pieChartData)
    pieChart.setTitle("Code Distribution")
    pieChart.setPrefSize(450, 400)
    pieChart.setLegendVisible(false)

    // Color the slices
    javafx.application.Platform.runLater(() => {
      if (pieChart.getData.size() >= 2) {
        pieChart.getData.get(0).getNode.setStyle("-fx-pie-color: #4CAF50;") // Live
        pieChart.getData.get(1).getNode.setStyle("-fx-pie-color: #f44336;")  // Dead
      }
    })

    pieChart
  }

  /**
   * Creates a bar chart showing the top 10 methods with the highest number of dead instructions
   * for a single-domain analysis result.
   * This visualizes which methods contribute most to dead code in the selected domain.
   * @param report The DeadCodeReport object representing the analysis result of a single domain
   * @return BarChart[String, Number] displaying top dead code methods and their dead instruction counts
   */
  private def createTopMethodsBarChart(report: DeadCodeReport): BarChart[String, Number] = {
    val xAxis = new CategoryAxis()
    val yAxis = new NumberAxis()
    val barChart = new BarChart[String, Number](xAxis, yAxis)

    xAxis.setLabel("Methods")
    yAxis.setLabel("Dead Instructions")
    barChart.setTitle("Top 10 Methods with Most Dead Code")
    barChart.setPrefSize(600, 400)

    val series = new XYChart.Series[String, Number]()
    series.setName("Dead Instructions")

    // Get top 10 methods with most dead instructions
    val topMethods = report.methodsFound
      .sortBy(-_.deadInstructions.size)
      .take(10)

    topMethods.foreach { method =>
      val methodName = method.fullSignature.split("\\(").head.split("\\.").last
      val shortName = if (methodName.length > 15) methodName.take(12) + "..." else methodName
      series.getData.add(new XYChart.Data(shortName, method.deadInstructions.size))
    }

    barChart.getData.add(series)
    barChart.setLegendVisible(false)

    barChart
  }

  /**
   * Creates a summary section for multi-domain analysis results.
   * Shows high-level statistics (domains analyzed, methods found, avg. methods per domain, total runtime)
   * and a comparison table of domains.
   * @param reports List of DeadCodeReport objects from all analyzed domains
   * @return VBox containing summary grid and a comparison table for the domains
   */
  private def createMultiDomainSummarySection(reports: List[DeadCodeReport]): VBox = {
    val section = new VBox(10)
    section.setStyle("-fx-background-color: #e3f2fd; -fx-padding: 15; -fx-background-radius: 5;")

    val sectionTitle = new Label("Multi-Domain Analysis Summary")
    sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;")

    val grid = new GridPane()
    grid.setHgap(20)
    grid.setVgap(8)

    val totalDomains = reports.size
    val totalMethodsAcrossAll = reports.map(_.methodsFound.size).sum
    val totalRuntimeAcrossAll = reports.map(_.totalRuntimeMs).sum
    val avgMethodsPerDomain = if (totalDomains > 0) totalMethodsAcrossAll.toDouble / totalDomains else 0.0

    val rows = List(
      GridRowData("Total Domains Analyzed", totalDomains.toString),
      GridRowData("Total Methods with Dead Code", totalMethodsAcrossAll.toString,
        if (totalMethodsAcrossAll > 0) "-fx-text-fill: #d32f2f;" else "-fx-text-fill: #388e3c;"),
      GridRowData("Average Methods per Domain", f"$avgMethodsPerDomain%.1f"),
      GridRowData("Total Analysis Runtime", s"${totalRuntimeAcrossAll}ms")
    )
    populateGrid(grid, rows)

    // Domain comparison table
    val comparisonLabel = new Label("Domain Comparison:")
    comparisonLabel.setStyle("-fx-font-weight: bold; -fx-margin: 10 0 5 0;")

    val comparisonTable = new TableView[DeadCodeReport]()
    comparisonTable.setPrefHeight(200)

    val domainColumn = new TableColumn[DeadCodeReport, String]("Domain")
    domainColumn.setCellValueFactory(data => new javafx.beans.property.SimpleStringProperty(
      data.getValue.domainUsed.take(30) + (if (data.getValue.domainUsed.length > 30) "..." else "")
    ))
    domainColumn.setPrefWidth(300)

    val methodsColumn = new TableColumn[DeadCodeReport, Number]("Methods Found")
    methodsColumn.setCellValueFactory(data => new javafx.beans.property.SimpleIntegerProperty(
      data.getValue.methodsFound.size
    ).asInstanceOf[javafx.beans.value.ObservableValue[Number]])
    methodsColumn.setPrefWidth(120)

    val runtimeColumn = new TableColumn[DeadCodeReport, String]("Runtime (ms)")
    runtimeColumn.setCellValueFactory(data => new javafx.beans.property.SimpleStringProperty(
      data.getValue.totalRuntimeMs.toString
    ))
    runtimeColumn.setPrefWidth(100)

    comparisonTable.getColumns.addAll(domainColumn, methodsColumn, runtimeColumn)

    val items: ObservableList[DeadCodeReport] = FXCollections.observableArrayList()
    reports.sortBy(-_.methodsFound.size).foreach(items.add)
    comparisonTable.setItems(items)

    section.getChildren.addAll(sectionTitle, grid, comparisonLabel, comparisonTable)
    section
  }

  /**
   * Creates a results pane for a single-domain analysis.
   * Displays the summary, charts, and detailed results table for the selected domain.
   * This is used as a tab content in the multi-domain results view (one tab per domain).
   * @param report The DeadCodeReport object representing the analysis result of a single domain
   * @return VBox containing the summary, charts, and detailed results table for this domain
   */
  private def createSingleDomainPane(report: DeadCodeReport): VBox = {
    val pane = new VBox(15)
    pane.setPadding(new Insets(10))

    // Summary Section
    val summarySection = createSummarySection(report)

    // Charts Section
    val chartsSection = createChartsSection(report)

    // Detailed Results Table
    val tableSection = createTableSection(report)

    pane.getChildren.addAll(summarySection, chartsSection, tableSection)
    pane
  }

  /**
   * Creates a detailed results table for a single-domain analysis.
   * Each row displays a method with dead code, its class, instruction counts, and dead code percentage.
   * Clicking on a row reveals the list of dead instructions for that method below the table.
   * Includes an "Export Results to JSON" button for saving the report.
   * @param report The DeadCodeReport object representing the analysis result of a single domain
   * @return VBox containing the table, interactive details, and export functionality
   */
  private def createTableSection(report: DeadCodeReport): VBox = {
    val section = new VBox(10)

    val sectionTitle = new Label("Detailed Results")
    sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;")

    // Main container for table and details
    val mainContainer = new VBox(10)

    // Table view
    val tableView = new TableView[MethodWithDeadCode]()
    tableView.setPrefHeight(300)

    // Create columns
    val classColumn = new TableColumn[MethodWithDeadCode, String]("Class")
    classColumn.setCellValueFactory(data => new javafx.beans.property.SimpleStringProperty(
      data.getValue.enclosingTypeName.split("\\.").last
    ))
    classColumn.setPrefWidth(120)

    val methodColumn = new TableColumn[MethodWithDeadCode, String]("Method")
    methodColumn.setCellValueFactory(data => new javafx.beans.property.SimpleStringProperty(
      data.getValue.fullSignature.split("\\(").head.split("\\.").last
    ))
    methodColumn.setPrefWidth(200)

    val totalInstColumn = new TableColumn[MethodWithDeadCode, Number]("Total Instructions")
    totalInstColumn.setCellValueFactory(data => new javafx.beans.property.SimpleIntegerProperty(
      data.getValue.numberOfTotalInstructions
    ).asInstanceOf[javafx.beans.value.ObservableValue[Number]])
    totalInstColumn.setPrefWidth(150)

    val deadInstColumn = new TableColumn[MethodWithDeadCode, Number]("Dead Instructions")
    deadInstColumn.setCellValueFactory(data => new javafx.beans.property.SimpleIntegerProperty(
      data.getValue.numberOfDeadInstructions
    ).asInstanceOf[javafx.beans.value.ObservableValue[Number]])
    deadInstColumn.setPrefWidth(150)

    val percentColumn = new TableColumn[MethodWithDeadCode, String]("Dead %")
    percentColumn.setCellValueFactory(data => {
      val method = data.getValue
      val percentage = method.numberOfDeadInstructions.toDouble / method.numberOfTotalInstructions * 100
      new javafx.beans.property.SimpleStringProperty(f"$percentage%.1f%%")
    })
    percentColumn.setPrefWidth(100)

    tableView.getColumns.addAll(classColumn, methodColumn, totalInstColumn, deadInstColumn, percentColumn)

    // Dead instructions details area
    val detailsContainer = new VBox(5)
    val detailsLabel = new Label("Dead Instructions Details:")
    detailsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;")
    detailsLabel.setVisible(false)

    val instructionsList = new ListView[String]()
    instructionsList.setPrefHeight(200)
    instructionsList.setVisible(false)
    instructionsList.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;")

    detailsContainer.getChildren.addAll(detailsLabel, instructionsList)

    // Add single-click handler to show dead instructions inline
    tableView.getSelectionModel.selectedItemProperty().addListener((_, _, newValue) => {
      if (newValue != null) {
        showDeadInstructionsInline(newValue, detailsLabel, instructionsList)
      } else {
        hideDeadInstructionsInline(detailsLabel, instructionsList)
      }
    })

    // Populate table
    val items: ObservableList[MethodWithDeadCode] = FXCollections.observableArrayList()
    report.methodsFound.sortBy(-_.numberOfDeadInstructions).foreach(items.add)
    tableView.setItems(items)

    // Add instruction label
    val instructionLabel = new Label("💡 Click on any row to view detailed dead instructions below")
    instructionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666; -fx-font-style: italic;")

    // Add export button
    val exportButton = new Button("Export Results to JSON")
    exportButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;")
    exportButton.setOnAction(_ => {
      val chooser = new FileChooser()
      chooser.setTitle("Save Analysis Results")
      chooser.getExtensionFilters.add(
        new FileChooser.ExtensionFilter("JSON Files", "*.json")
      )
      chooser.setInitialFileName("dead_code_analysis_results.json")

      val file = chooser.showSaveDialog(exportButton.getScene.getWindow)
      if (file != null) {
        try {
          JsonIO.writeResult(report, file.getAbsolutePath)
          val alert = new Alert(Alert.AlertType.INFORMATION)
          alert.setTitle("Export Successful")
          alert.setHeaderText(null)
          alert.setContentText(s"Results exported to: ${file.getAbsolutePath}")
          alert.showAndWait()
        } catch {
          case ex: Exception =>
            val alert = new Alert(Alert.AlertType.ERROR)
            alert.setTitle("Export Failed")
            alert.setHeaderText("Failed to export results")
            alert.setContentText(s"Error: ${ex.getMessage}")
            alert.showAndWait()
        }
      }
    })

    val buttonBox = new HBox(10)
    buttonBox.setAlignment(Pos.CENTER_RIGHT)
    buttonBox.getChildren.addAll(instructionLabel, exportButton)

    mainContainer.getChildren.addAll(tableView, detailsContainer, buttonBox)
    section.getChildren.addAll(sectionTitle, mainContainer)
    section
  }

  /**
   * Shows the list of dead instructions for a selected method in the single-domain table.
   * Updates the label and makes the instructions ListView visible below the table.
   * @param method The selected MethodWithDeadCode object
   * @param detailsLabel Label UI element for showing method info (updated with the method name and class)
   * @param instructionsList ListView UI element to display each dead instruction (PC + representation)
   */
  private def showDeadInstructionsInline(method: MethodWithDeadCode, detailsLabel: Label, instructionsList: ListView[String]): Unit = {
    // Update label
    detailsLabel.setText(s"Dead Instructions for: ${method.fullSignature.split("\\(").head.split("\\.").last} (Class: ${method.enclosingTypeName.split("\\.").last})")
    detailsLabel.setVisible(true)

    // Update instructions list
    val instructionsItems: ObservableList[String] = FXCollections.observableArrayList()
    method.deadInstructions.foreach { deadInst =>
      instructionsItems.add(s"PC: ${deadInst.programCounter} - ${deadInst.stringRepresentation}")
    }
    instructionsList.setItems(instructionsItems)
    instructionsList.setVisible(true)
  }

  /**
   * Hides the dead instructions detail panel.
   * Used when no method is selected in the table or to reset the UI.
   * @param detailsLabel Label UI element that displays method information
   * @param instructionsList ListView UI element showing the dead instructions
   */
  private def hideDeadInstructionsInline(detailsLabel: Label, instructionsList: ListView[String]): Unit = {
    detailsLabel.setVisible(false)
    instructionsList.setVisible(false)
  }

  /**
   * Runs the dead code analysis for a given domain index and configuration.
   * Prepares the OPAL Project instance for the selected project and library JARs,
   * looks up the domain by index, and triggers the analysis.
   * @param domainIndex Index of the domain to use from DomainRegistry
   * @param config The AnalysisConfig object containing input and analysis parameters
   * @return DeadCodeReport containing results of the analysis for the selected domain
   */
  private def runAnalysisWithSelectedDomain(domainIndex: Int, config: AnalysisConfig): DeadCodeReport = {
    val project: Project[URL] = {
      implicit val logContext: GlobalLogContext.type = GlobalLogContext
      val opalConfig = ConfigFactory.load()

      val projectFilesArray = config.projectJars.toArray
      val libraryFilesArray = config.libraryJars.toArray

      Project(
        projectFilesArray,
        libraryFilesArray,
        logContext,
        opalConfig
      )
    }

    val domainDescriptions = DomainRegistry.domainDescriptions.toList
    val selectedDomainStr = if (domainIndex >= 0 && domainIndex < domainDescriptions.length) {
      domainDescriptions(domainIndex)
    } else {
      domainDescriptions.head
    }

    val cleanDomainName = if (selectedDomainStr.contains("]")) {
      selectedDomainStr.substring(selectedDomainStr.indexOf("[") + 1, selectedDomainStr.indexOf("]"))
    } else {
      selectedDomainStr
    }

    val cleanDomainStr = if (selectedDomainStr.contains("]")) {
      selectedDomainStr.substring(selectedDomainStr.indexOf("]") + 2)
    } else {
      selectedDomainStr
    }

    DeadCodeAnalysis.analyze(project, cleanDomainStr, cleanDomainName, config)
  }

  /**
   * Creates a temporary JSON configuration file for use during analysis.
   * This method serializes the given AnalysisConfig object to JSON and writes it
   * to a uniquely named file in the system temporary directory.
   * Used to pass analysis configuration data between components or subprocesses.
   * @param config The AnalysisConfig object to serialize
   * @return File object pointing to the created temporary JSON config file
   */
  private def createTempConfig(config: AnalysisConfig): File = {
    val configJson = Json.obj(
      "projectJars" -> config.projectJars.map(_.getAbsolutePath),
      "libraryJars" -> config.libraryJars.map(_.getAbsolutePath),
      "completelyLoadLibraries" -> config.completelyLoadLibraries,
      "interactive" -> config.interactive,
      "outputJson" -> config.outputJson
    )

    val tempFile = new File(System.getProperty("java.io.tmpdir"), s"temp_config_${System.currentTimeMillis()}.json")
    val writer = new PrintWriter(tempFile)
    writer.write(Json.prettyPrint(configJson))
    writer.close()
    tempFile
  }

  /**
   * Recursively searches for JSON configuration files in the given directory and its subdirectories.
   * Only files that appear to be analysis configuration files are returned (based on simple heuristics).
   * Ignores hidden folders and the "target" build folder.
   * @param directory The root directory to start searching from
   * @return List of File objects representing likely config JSON files
   */
  private def findConfigFiles(directory: File): List[File] = {
    val configs = ListBuffer[File]()

    def searchRecursively(dir: File): Unit = {
      if (dir.exists() && dir.isDirectory) {
        dir.listFiles().foreach { file =>
          if (file.isFile && file.getName.toLowerCase.endsWith(".json")) {
            if (isLikelyConfigFile(file)) {
              configs += file
            }
          } else if (file.isDirectory && !file.getName.startsWith(".") && file.getName != "target") {
            searchRecursively(file)
          }
        }
      }
    }

    searchRecursively(directory)
    configs.toList.sortBy(_.getName)
  }

  /**
   * Heuristically determines whether a given JSON file is likely to be a valid analysis configuration file.
   * This function checks for the presence of certain keywords in the file content or filename.
   * Used to avoid accidentally selecting unrelated JSON files during auto-detection.
   * @param file The File object to check
   * @return true if the file looks like a config file, false otherwise
   */
  private def isLikelyConfigFile(file: File): Boolean = {
    Try {
      val source = scala.io.Source.fromFile(file)
      val content = try source.mkString finally source.close()
      content.contains("projectJars") ||
        file.getName.toLowerCase.contains("config") ||
        file.getName.toLowerCase.contains("analysis")
    } match {
      case Success(result) => result
      case Failure(_) => false
    }
  }
}