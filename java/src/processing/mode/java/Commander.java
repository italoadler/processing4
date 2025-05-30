/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-19 The Processing Foundation
  Copyright (c) 2008-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package processing.mode.java;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import processing.app.Base;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.RunnerListener;
import processing.app.Sketch;
import processing.mode.java.preproc.SketchException;
import processing.app.Util;
import processing.app.contrib.ModeContribution;
import processing.core.PApplet;
import processing.data.StringDict;
import processing.mode.java.runner.Runner;


/**
 * Class to handle running Processing from the command line.
 */
public class Commander implements RunnerListener {
  static final String helpArg = "--help";
//  static final String preprocArg = "--preprocess";
  static final String buildArg = "--build";
  static final String runArg = "--run";
  static final String presentArg = "--present";
  static final String sketchArg = "--sketch=";
  static final String forceArg = "--force";
  static final String outputArg = "--output=";
  static final String exportApplicationArg = "--export";
  static final String noJavaArg = "--no-java";
//  static final String platformArg = "--platform=";
//  static final String bitsArg = "--bits=";
//  static final String preferencesArg = "--preferences=";
  static final String variantArg = "--variant=";

  static final int HELP = -1;
//  static final int PREPROCESS = 0;
  static final int BUILD = 1;
  static final int RUN = 2;
  static final int PRESENT = 3;
  static final int EXPORT = 4;

  Sketch sketch;

  PrintStream systemOut;
  PrintStream systemErr;


  public Commander(String[] args) {
    String sketchPath = null;
    File sketchFolder = null;
    String pdePath = null;  // path to the .pde file
    String outputPath = null;
    File outputFolder = null;
    boolean outputSet = false;  // set an output folder
    boolean force = false;  // replace that no good output folder
//    String preferencesPath = null;
//    int platform = PApplet.platform; // default to this platform
//    int platformBits = Base.getNativeBits();
    String variant = Platform.getVariant();
    int task = HELP;
    boolean embedJava = true;

    if (Platform.isWindows()) {
      // On Windows, it needs to use the default system encoding.
      // https://github.com/processing/processing/issues/1633
      systemOut = new PrintStream(System.out, true);
      systemErr = new PrintStream(System.err, true);
    } else {
      // OS X formerly used MacRoman or something else useless.
      // (Not sure about Linux, but this has worked since 2.0)
      // https://github.com/processing/processing/issues/1456
      systemOut = new PrintStream(System.out, true, StandardCharsets.UTF_8);
      systemErr = new PrintStream(System.err, true, StandardCharsets.UTF_8);
    }

    int argOffset = 0;
    for (String arg : args) {
      argOffset++;

      if (arg.length() == 0) {
        // ignore it, just the crappy shell script

      } else if (arg.equals(helpArg)) {
        // mode already set to HELP

//      } else if (arg.equals(preprocArg)) {
//        task = PREPROCESS;

      } else if (arg.equals(buildArg)) {
        task = BUILD;
        break;

      } else if (arg.equals(runArg)) {
        task = RUN;
        break;

      } else if (arg.equals(presentArg)) {
        task = PRESENT;
        break;

      } else if (arg.equals(exportApplicationArg)) {
        task = EXPORT;
        break;

      } else if (arg.equals(noJavaArg)) {
        embedJava = false;

      } else if (arg.startsWith("--platform")) {
        complainAndQuit("Use --variant instead of --platform with Processing 4.", true);

      } else if (arg.startsWith(variantArg)) {
        variant = arg.substring(variantArg.length());

      } else if (arg.startsWith(sketchArg)) {
        sketchPath = arg.substring(sketchArg.length());
        sketchFolder = new File(sketchPath);
        if (!sketchFolder.exists()) {
          complainAndQuit(sketchFolder + " does not exist.", false);
        }
        File pdeFile = new File(sketchFolder, sketchFolder.getName() + ".pde");
        if (!pdeFile.exists()) {
          complainAndQuit("Not a valid sketch folder. " + pdeFile + " does not exist.", true);
        }
        pdePath = pdeFile.getAbsolutePath();

//      } else if (arg.startsWith(preferencesArg)) {
//        preferencesPath = arg.substring(preferencesArg.length());

      } else if (arg.startsWith(outputArg)) {
        outputSet = true;
        outputPath = arg.substring(outputArg.length());

      } else if (arg.equals(forceArg)) {
        force = true;

      } else {
        complainAndQuit("I don't know anything about " + arg + ".", true);
      }
    }
    String[] sketchArgs = PApplet.subset(args, argOffset);

    if (task == HELP) {
      printCommandLine(systemOut);
      System.exit(0);
    }

    if (outputSet) {
      if (outputPath == null) {
        complainAndQuit("An output path must be specified.", true);
      }

      outputFolder = new File(outputPath);
      if (outputFolder.exists()) {
        if (force) {
          Util.removeDir(outputFolder);
        } else {
          complainAndQuit("The output folder already exists. " +
                          "Use --force to remove it.", false);
        }
      }

      if (!outputFolder.mkdirs()) {
        complainAndQuit("Could not create the output folder.", false);
      }
    }

//    // run static initialization that grabs all the prefs
//    // (also pass in a prefs path if that was specified)
//    if (preferencesPath != null) {
//      Preferences.init(preferencesPath);
//    }

    Preferences.init();
    Base.locateSketchbookFolder();

    if (sketchPath == null) {
      complainAndQuit("No sketch path specified.", true);

//    } else if (!pdePath.toLowerCase().endsWith(".pde")) {
//      complainAndQuit("Sketch path must point to the main .pde file.", false);

    } else {

      if (outputSet) {
        if (outputPath.equals(sketchPath)) {
          complainAndQuit("The sketch path and output path cannot be identical.", false);
        }
      }

      boolean success = false;

      JavaMode javaMode = (JavaMode)
        ModeContribution.load(null, Platform.getContentFile("modes/java"),
                              "processing.mode.java.JavaMode").getMode();
      try {
        sketch = new Sketch(pdePath, javaMode);

        if (!outputSet) {
          outputFolder = sketch.makeTempFolder();
        }

        if (task == BUILD || task == RUN || task == PRESENT) {
          JavaBuild build = new JavaBuild(sketch);
          File srcFolder = new File(outputFolder, "source");
          String className = build.build(srcFolder, outputFolder, true);
//          String className = build.build(sketchFolder, outputFolder, true);
          if (className != null) {
            success = true;
            if (task == RUN || task == PRESENT) {
              Runner runner = new Runner(build, this);
              if (task == PRESENT) {
                runner.present(sketchArgs);
              } else {
                runner.launch(sketchArgs);
              }
              success = !runner.vmReturnedError();
            }
          } else {
            success = false;
          }

        } else if (task == EXPORT) {
          if (outputPath == null) {
            javaMode.handleExportApplication(sketch);
          } else {
            JavaBuild build = new JavaBuild(sketch);
            build.build(true);
            if (build != null) {
              success = build.exportApplication(outputFolder, variant, embedJava);
            }
          }
        }
        if (!success) {  // error already printed
          System.exit(1);
        }
        systemOut.println("Finished.");
        System.exit(0);

      } catch (SketchException re) {
        statusError(re);
        System.exit(1);

      } catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }
  }


  public void statusNotice(String message) {
    systemErr.println(message);
  }


  public void statusError(String message) {
    systemErr.println(message);
  }


  public void statusError(Exception exception) {
    if (exception instanceof SketchException) {
      SketchException re = (SketchException) exception;

      int codeIndex = re.getCodeIndex();
      if (codeIndex != -1) {
        // format the runner exception like emacs
        //blah.java:2:10:2:13: Syntax Error: This is a big error message
        // Emacs doesn't like the double line thing coming from Java
        // https://github.com/processing/processing/issues/2158
        String filename = sketch.getCode(codeIndex).getFileName();
        int line = re.getCodeLine() + 1;
        int column = re.getCodeColumn() + 1;
        //if (column == -1) column = 0;
        // TODO if column not specified, should just select the whole line.
        // But what's the correct syntax for that?
        systemErr.println(filename + ":" +
          line + ":" + column + ":" +
          line + ":" + column + ":" + " " + re.getMessage());

      } else {  // no line number, pass the trace along to the user
        exception.printStackTrace();
      }
    } else {
      exception.printStackTrace();
    }
  }


  void complainAndQuit(String lastWords, boolean schoolEmFirst) {
    if (schoolEmFirst) {
      printCommandLine(systemErr);
    }
    systemErr.println(lastWords);
    System.exit(1);
  }


  static void printCommandLine(PrintStream out) {
    out.println();
    out.println("Command line edition for Processing " + Base.getVersionName() + " (Java Mode)");
    out.println();
    out.println("--help               Show this help text. Congratulations.");
    out.println();
    out.println("--sketch=<name>      Specify the sketch folder (required)");
    out.println("--output=<name>      Specify the output folder (optional and");
    out.println("                     cannot be the same as the sketch folder.)");
    out.println();
    out.println("--force              The sketch will not build if the output");
    out.println("                     folder already exists, because the contents");
    out.println("                     will be replaced. This option erases the");
    out.println("                     folder first. Use with extreme caution!");
    out.println();
    out.println("--build              Preprocess and compile a sketch into .class files.");
    out.println("--run                Preprocess, compile, and run a sketch.");
    out.println("--present            Preprocess, compile, and run a sketch in presentation mode.");
    out.println();
    out.println("--export             Export an application.");
//    out.println("--platform           Specify the platform (export to application only).");
//    out.println("                     Should be one of 'windows', 'macosx', or 'linux'.");
    out.println("--variant            Specify the platform and architecture (Export only).");
    out.println("--no-java            Do not embed Java.");
    out.println();

    out.println("Starting with 4.0, the --platform option has been removed");
    out.println("because of the variety of platforms and architectures now available.");
    out.println("Use the --variant option instead, for instance:");
    out.println();
    /*
    out.println("platform                     variant");
    out.println("---------------------------  -------------");
    out.println("macOS (Intel 64-bit)         macos-x86_64");
    out.println("macOS (Apple Silicon)        macos-aarch64");
    out.println("Windows (Intel 64-bit)       windows-amd64");
    out.println("Linux (Intel 64-bit)         linux-amd64");
    out.println("Linux (Raspberry Pi 32-bit)  linux-arm");
    out.println("Linux (Raspberry Pi 64-bit)  linux-aarch64");
    */
    out.println("variant        platform");
    out.println("-------------  ---------------------------");

    StringDict variants = Platform.getSupportedVariants();
    for (String key : variants.keys()) {
      out.print(key);
      int spaces = 15 - key.length();  // 13 dashes, two spaces
      for (int i = 0; i < spaces; i++) {
        System.out.print(" ");
      }
      System.out.println(variants.get(key));
    }
    out.println();

    out.println("The --build, --run, --present, or --export must be the final parameter");
    out.println("passed to Processing. Arguments passed following one of those four will");
    out.println("be passed through to the sketch itself, and therefore available to the");
    out.println("sketch via the 'args' field. To pass options understood by PApplet.main(),");
    out.println("write a custom main() method so that the preprocessor does not add one.");
    out.println("https://github.com/processing/processing/wiki/Command-Line");
    out.println();
  }


  @Override
  public void startIndeterminate() { }


  @Override
  public void stopIndeterminate() { }


  @Override
  public void statusHalt() { }


  @Override
  public boolean isHalted() {
    return false;
  }


  static public void main(String[] args) {
    // Do this early so that error messages go to the console
    Base.setCommandLine();

    // init the platform so that prefs and other native code is ready to go
    Platform.init();

    // launch command line handler
    new Commander(args);
  }
}
