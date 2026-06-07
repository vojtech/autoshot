class Autoshot < Formula
  desc "CLI processor for Android screenshot test automation"
  homepage "https://github.com/vojtech/screenshot-test-automator"
  url "https://github.com/vojtech/screenshot-test-automator/releases/download/v1.0.0-alpha01/autoshot-processor-1.0.0-alpha01-standalone.jar"
  sha256 "cf015bd1d5f652f51e7f8008263fc5fe7b14172376aeefa8ef0b55b928fe2cb9"
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
