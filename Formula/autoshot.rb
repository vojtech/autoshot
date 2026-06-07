class Autoshot < Formula
  desc "CLI processor for Android screenshot test automation"
  homepage "https://github.com/vojtech/screenshot-test-automator"
  url "https://github.com/vojtech/screenshot-test-automator/releases/download/v1.0.0-alpha01/autoshot-processor-1.0.0-alpha01-standalone.jar"
  sha256 "3959df53872d327a38c560b14a4413a6d7ed62852ff68786885ef9b9d782e6d6"
  license "Apache-2.0"

  depends_on "openjdk"

  def install
    libexec.install "autoshot-processor-#{version}-standalone.jar" => "autoshot-processor.jar"
    bin.write_jar_script(
      libexec/"autoshot-processor.jar",
      "autoshot",
      main_class: "com.fediim.autoshot.processor.CliMain",
    )
  end

  test do
    system "#{bin}/autoshot", "--help"
  end
end
