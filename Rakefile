# Common usage
#
#   rake build:adapters - to build all specific adapter gems and the base gem
#   rake release:do - build:adapters + git tag + push gems
#
# Environment variables used by this Rakefile:
#
# DBS
#   Limits the command performed to only work for one of the database
#   types listed in this env var. You can set to a combination of mysql, 
#   postgres, or sqlite, separated by commas. For example:
#
#    mysql,postgres,sqlite
#
#    You may use pg or postgres as aliases for postgresql
#    You may use sqlite3 as an alias for sqlite
#    You may use all to mean all three
#
# INCLUDE_JAR_IN_GEM [default task - false, other taks - true]:
#   Note: This is something you should not normally have to set.
#   For local development we always will end up including the jar file
#   in any task which generates our main gem.  The wrinkle to this
#   is when we do a custom github link in bundler:
#
#      gem 'ar-jdbc', github: '...'
#
#   Because we stopped committing a .jar file for every change and so when
#   we  include a gem like this it clones the repository and does a default
#   build in rake.  This in turn will end up forcing a compile to generate
#   that jar (similar to how c-extensions compile at the time of install).
#   For shipped gems we do include the jar so that people do not require
#   this compile step.
#
# NOOP [release:do - false]
#
#   No commands or gem pushing during a release.

require 'rake/clean'

CLEAN.include 'test.db.*', '*test.sqlite3', 'test/reports'
CLEAN.include 'lib/**/*.jar', 'MANIFEST.MF', '*.log', 'target/*'

task :default => :jar # RubyGems extention will do a bare `rake' e.g. :
# jruby" -rubygems /opt/local/rvm/gems/jruby-1.7.16@jdbc/gems/rake-10.3.2/bin/rake
#   RUBYARCHDIR=/opt/local/rvm/gems/jruby-1.7.16@jdbc/gems/activerecord-jdbc-adapter-1.4.0.dev/lib
#   RUBYLIBDIR=/opt/local/rvm/gems/jruby-1.7.16@jdbc/gems/activerecord-jdbc-adapter-1.4.0.dev/lib
#
# under Bundler it points those DIRs to an empty one where only built extensions are stored :
# jruby -rubygems /opt/local/rvm/gems/jruby-1.7.19@temp/gems/rake-10.4.2/bin/rake
#   RUBYARCHDIR=/opt/local/rvm/gems/jruby-1.7.19@temp/bundler/gems/extensions/universal-java-1.7/1.9/activerecord-jdbc-adapter-472b5fddba43
#   RUBYLIBDIR=/opt/local/rvm/gems/jruby-1.7.19@temp/bundler/gems/extensions/universal-java-1.7/1.9/activerecord-jdbc-adapter-472b5fddba43
#
# javac -target 1.6 -source 1.6 -Xlint:unchecked -g -cp
#   "/opt/local/rvm/rubies/jruby-1.7.19/lib/jruby.jar:
#    /opt/local/maven-repo/mysql/mysql-connector-java/5.1.33/mysql-connector-java-5.1.33.jar:
#    /opt/local/maven-repo/org/postgresql/postgresql/9.4-1200-jdbc4/postgresql-9.4-1200-jdbc4.jar"
#   -d /tmp/d20150417-32100-7mb1bk src/java/arjdbc/ArJdbcModule.java ...


base_dir = Dir.pwd
gem_name = 'activerecord-jdbc-adapter'
gemspec_path = File.expand_path('activerecord-jdbc-adapter.gemspec', File.dirname(__FILE__))
gemspec = lambda do
  @_gemspec_ ||= Dir.chdir(File.dirname(__FILE__)) do
    Gem::Specification.load(gemspec_path)
  end
end
built_gem_path = lambda do
  Dir[File.join(base_dir, "#{gem_name}-*.gem")].sort_by{ |f| File.mtime(f) }.last
end
current_version = lambda { gemspec.call.version }
rake = lambda { |task| ruby "-S", "rake", task }

# NOTE: avoid Bundler loading due native extension building!

desc "Build #{gem_name} gem into the pkg directory."
task :build => :jar do
  include_jar = ENV['INCLUDE_JAR_IN_GEM'] || 'true'
  sh("INCLUDE_JAR_IN_GEM=#{include_jar} gem build -V '#{gemspec_path}'") do
    gem_path = built_gem_path.call
    file_name = File.basename(gem_path)
    FileUtils.mkdir_p(File.join(base_dir, 'pkg'))
    FileUtils.mv(gem_path, 'pkg')
    puts "\n#{gem_name} #{current_version.call} built to 'pkg/#{file_name}'"
  end
end

desc "Build and install #{gem_name} gem into system gems."
task :install => :build do
  gem_path = built_gem_path.call
  sh("gem install '#{gem_path}' --local") do |ok|
    raise "Couldn't install gem, run `gem install #{gem_path}' for more detailed output" unless ok
    puts "\n#{gem_name} (#{current_version.call}) installed"
  end
end

desc "Releasing AR-JDBC gems (use NOOP=true to disable gem pushing)"
task 'release:do' do
  Rake::Task['build'].invoke
  Rake::Task['build:adapters'].invoke

  noop = ENV.key?('NOOP') && (ENV['NOOP'] != 'false' && ENV['NOOP'] != '')

  version = current_version.call; version_tag = "v#{version}"

  sh("git diff --no-patch --exit-code", :noop => noop) { |ok| fail "git working dir is not clean" unless ok }
  sh("git diff-index --quiet --cached HEAD", :noop => noop) { |ok| fail "git index is not clean" unless ok }

  sh "git tag -a -m \"AR-JDBC #{version}\" #{version_tag}", :noop => noop
  branch = `git rev-parse --abbrev-ref HEAD`.strip
  puts "releasing from (current) branch #{branch.inspect}"
  sh "for gem in `ls pkg/*-#{version}-java.gem`; do gem push $gem; done", :noop => noop do |ok|
    sh "git push origin #{branch} --tags", :noop => noop if ok
  end
end

task 'release:push' do
  sh "for gem in `ls pkg/*-#{current_version.call}-java.gem`; do gem push $gem; done"
end

DB_ALIASES = {
  'mysql'      => 'mysql',
  'postgresql' => 'postgresql',
  'postgres'   => 'postgresql',
  'pg'         => 'postgresql',
  'sqlite3'    => 'sqlite3',
  'sqlite'     => 'sqlite3'
}

def invalid_dbs!
  raise ArgumentError, "Invalid DBS env var\nThe DBS env var must be set to a combination of mysql, postgres, or " \
                       "sqlite, separated by commas. For example:\n\nmysql,postgres,sqlite\n\nYou may use pg or " \
                       "postgres as aliases for postgresql\nYou may use sqlite3 as an alias for sqlite\n" \
                       "You may use all to mean all three"
end

def make_db_list
ENV["DBS"] = "mysql,postgresql,sqlite3" if ENV["DBS"] == "all" || ENV["DBS"].nil? || ENV["DBS"].strip.empty?
requested = ENV["DBS"].split(",").map(&:strip).reject(&:empty?).map(&:downcase)
  invalid_dbs! unless requested.size > 0 && requested.size <= 3 && requested == requested.uniq

  canonical = requested.map do |name|
    DB_ALIASES.fetch(name) { invalid_dbs! }
  end

  invalid_dbs! unless canonical == canonical.uniq

  canonical
end

db_list = make_db_list
ADAPTERS = db_list.map { |db| "activerecord-jdbc#{db}-adapter" }

db_list.map! {|db| db == 'postgresql' ? 'postgres' : db  }  #naming convention for DRIVERS
DRIVERS  = db_list.map { |a| "jdbc-#{a}" }

TARGETS = ( ADAPTERS + DRIVERS )

ADAPTERS.each do |target|
  namespace target do
    task :build do
      version = current_version.call
      Dir.chdir(target) { rake.call "build" }
      cp FileList["#{target}/pkg/#{target}-#{version}-java.gem"], "pkg"
    end
  end
end
DRIVERS.each do |target|
  namespace target do
    task :build do
      Dir.chdir(target) { rake.call "build" }
      cp FileList["#{target}/pkg/#{target}-*.gem"], "pkg"
    end
  end
end
TARGETS.each do |target|
  namespace target do
    task :install do
      Dir.chdir(target) { rake.call "install" }
    end
    #task :release do
    #  Dir.chdir(target) { rake.call "release" }
    #end
  end
end

# DRIVERS

desc "Build drivers"
task "build:drivers" => DRIVERS.map { |name| "#{name}:build" }
task "drivers:build" => 'build:drivers'

desc "Install drivers"
task "install:drivers" => DRIVERS.map { |name| "#{name}:install" }
task "drivers:install" => 'install:drivers'

# ADAPTERS

desc "Build adapters"
task "build:adapters" => [ 'build' ] + ADAPTERS.map { |name| "#{name}:build" }
task "adapters:build" => 'build:adapters'

desc "Install adapters"
task "install:adapters" => [ 'install' ] + ADAPTERS.map { |name| "#{name}:install" }
task "adapters:install" => 'install:adapters'

# ALL

task "build:all" => [ 'build' ] + TARGETS.map { |name| "#{name}:build" }
task "all:build" => 'build:all'
task "install:all" => [ 'install' ] + TARGETS.map { |name| "#{name}:install" }
task "all:install" => 'install:all'

require 'rake/testtask'

# native JRuby extension (adapter_java.jar) compilation :

if defined? JRUBY_VERSION
  jar_file = 'lib/arjdbc/jdbc/adapter_java.jar'; CLEAN << jar_file
  desc "Compile the native (Java) extension."
  task :jar => jar_file

  namespace :jar do
    task :force do
      rm jar_file if File.exist?(jar_file)
      Rake::Task['jar'].invoke
    end
  end

  #directory classes = 'pkg/classes'; CLEAN << classes

  file jar_file => FileList[ 'src/java/**/*.java' ] do
    source = target = '21'; debug = true

    get_driver_jars_local = lambda do |*args|
      driver_deps = args.empty? ? [ :Postgres, :MySQL ] : args
      driver_jars = []
      driver_deps.each do |name|
        driver_jars << Dir.glob("jdbc-#{name.to_s.downcase}/lib/*.jar").sort.last
      end
      if driver_jars.empty? # likely on a `gem install ...'
        # NOTE: we're currently assuming jdbc-xxx (compile) dependencies are
        # installed, they are declared as gemspec.development_dependencies !
        # ... the other option is to simply `mvn prepare-package'
        driver_deps.each do |name|
          #require "jdbc/#{name.to_s.downcase}"
          #driver_jars << Jdbc.const_get(name).driver_jar
          # thanks Bundler for mocking RubyGems completely :
          #spec = Gem::Specification.find_by_name("jdbc-#{name.to_s.downcase}")
          #driver_jars << Dir.glob(File.join(spec.gem_dir, 'lib/*.jar')).sort.last
          gem_name = "jdbc-#{name.to_s.downcase}"; matched_gem_paths = []
          Gem.paths.path.each do |path|
            base_path = File.join(path, "gems/")
            Dir.glob(File.join(base_path, "*")).each do |gem_path|
              if gem_path.sub(base_path, '').start_with?(gem_name)
                matched_gem_paths << gem_path
              end
            end
          end
          if gem_path = matched_gem_paths.sort.last
            driver_jars << Dir.glob(File.join(gem_path, 'lib/*.jar')).sort.last
          end
        end
      end
      driver_jars
    end

    get_driver_jars_maven = lambda do
      require 'jar_dependencies'

      requirements = gemspec.call.requirements
      match_driver_jars = lambda do
        matched_jars = []
        gemspec.call.requirements.each do |requirement|
          if match = requirement.match(/^jar\s+([\w\-\.]+):([\w\-]+),\s+?([\w\.\-]+)?/)
            matched_jar = Jars.send :to_jar, match[1], match[2], match[3], nil
            matched_jar = File.join( Jars.home, matched_jar )
            matched_jars << matched_jar if File.exists?( matched_jar )
          end
        end
        matched_jars
      end

      driver_jars = match_driver_jars.call
      if driver_jars.size < requirements.size
        if (ENV['JARS_SKIP'] || ENV_JAVA['jars.skip']) == 'true'
          warn "jar resolving skipped, extension might not compile"
        else
          require 'jars/installer'
          ENV['JARS_QUIET'] = 'true'
          puts "resolving jar dependencies to build extension (should only happen once) ..."
          installer = Jars::Installer.new( gemspec_path )
          installer.install_jars( false )

          driver_jars = match_driver_jars.call
        end
      end

      driver_jars
    end

    # TODO not good but since jar-dependencies doesn't do the job, let's default to local (for now)
    if ENV['BUILD_EXT_MAVEN'] == 'true' # || ENV['RUBYARCHDIR']
      driver_jars = get_driver_jars_maven.call
    else
      driver_jars = get_driver_jars_local.call
    end

    classpath = []
    require 'rbconfig'
    libdir = RbConfig::CONFIG['libdir']
    if libdir.start_with? 'classpath:'
      error "Cannot build activerecord-jdbc with jruby-complete"
    end
    classpath << File.join(libdir, 'jruby.jar')
    classpath += driver_jars
    classpath = classpath.compact.join(File::PATH_SEPARATOR)

    source_files = FileList[ 'src/java/**/*.java' ]

    version = lambda do
      begin
        require 'arjdbc/version'
      rescue LoadError
        path = File.expand_path('lib', File.dirname(__FILE__))
        unless $LOAD_PATH.include?(path)
          $LOAD_PATH << path; retry
        end
      end

      gem_version = Gem::Version.create(ArJdbc::VERSION)
      if gem_version.segments.last == 'DEV'
        gem_version.segments[0...-1] # 50.0.DEV -> 50.0
      else
        gem_version.segments.dup
      end
    end

    require 'tmpdir'; Dir.mktmpdir do |classes_dir|
      # Cross-platform way of finding an executable in the $PATH. Thanks to @mislav
      which = lambda do |cmd|
        exts = ENV['PATHEXT'] ? ENV['PATHEXT'].split(';') : ['']
        ENV['PATH'].split(File::PATH_SEPARATOR).map do |path|
          exts.map { |ext| File.join(path, "#{cmd}#{ext}") }
        end.flatten.select{|f| File.executable? f}.first
      end

      args = [ '-Xlint:unchecked' ]

      unless javac = which.call('javac')
        warn "could not find javac, please make sure it's on the PATH"
      end
      javac = "#{javac} -target #{target} -source #{source} #{args.join(' ')}"
      javac << " #{debug ? '-g' : ''}"
      javac << " -cp \"#{classpath}\" -d #{classes_dir} #{source_files.join(' ')}"
      sh(javac) do |ok|
        raise 'could not build .jar extension - compilation failure' unless ok
      end

      # class_files = FileList["#{classes_dir}/**/*.class"].gsub("#{classes_dir}/", '')
      # avoid environment variable expansion using backslash
      # class_files.gsub!('$', '\$') unless windows?
      # args = class_files.map { |path| [ "-C #{classes_dir}", path ] }.flatten

      if ENV['INCLUDE_JAR_IN_GEM'] == 'true'; require 'tempfile'
        manifest  = "Built-Time: #{Time.now.utc.strftime('%Y-%m-%d %H:%M:%S')}\n"
        manifest += "Built-JRuby: #{JRUBY_VERSION}\n"
        manifest += "Specification-Title: ActiveRecord-JDBC\n"
        manifest += "Specification-Vendor: JRuby\n"
        manifest += "Specification-Version: #{version.call[0].to_s.split('').join('.')}\n" # AR VERSION (52 -> 5.2)
        manifest += "Implementation-Vendor: The JRuby Team\n"
        manifest += "Implementation-Version: #{version.call.join('.')}\n"
        manifest  = Tempfile.new('MANIFEST').tap { |f| f << manifest; f.close }.path
      end

      args = []; opts = '-cf'
      if manifest
        opts = "#{opts}m"
        args = [ "#{manifest}" ]
      end
      args += [ '-C', "#{classes_dir}/ ." ]

      jar_path = jar_file
      if ext_lib_dir = ENV['RUBYLIBDIR']
        jar_path = File.join(ext_lib_dir, File.basename(jar_file))
      end

      unless jar = which.call('jar')
        warn "could not find jar tool, please make sure it's on the PATH"
      end
      sh("#{jar} #{opts} #{jar_path} #{args.join(' ')}") do |ok|
        raise 'could not build .jar extension - packaging failure' unless ok
      end
      cp jar_path, jar_file if ext_lib_dir # NOTE: hopefully RG won't mind?!
    end
  end
else
  task :jar do
    warn "please run `rake jar' under JRuby to re-compile the native (Java) extension"
  end
end
